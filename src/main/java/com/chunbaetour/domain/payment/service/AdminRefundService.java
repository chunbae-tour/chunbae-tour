package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import com.chunbaetour.domain.common.util.CursorUtils;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 환불 처리 서비스 (STORY-07).
 * 승인: order 상태 전이 → refund 상태 전이 → 엽전 회수 → PG 취소 (단일 트랜잭션).
 * PG 취소 실패 시 전체 롤백 — DB와 금전 상태 일관성 보장.
 * 거절: Refund 상태만 REJECTED로 변경.
 * 락 획득 순서: Refund → PaymentOrder → Wallet (데드락 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRefundService {

    private final RefundRepository refundRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;

    /**
     * 환불 승인.
     * 단일 트랜잭션 내에서 order.refund() → refund.approve() → 엽전 회수 → PG 취소 순으로 실행.
     * PG 취소 실패 시 트랜잭션 전체 롤백 — Refund PENDING, PaymentOrder COMPLETED, 엽전 원상 복구.
     * 관리자는 재시도 가능.
     * <p>
     * TODO [FUTURE]: PG 취소 요청 시 refundId 기반 멱등키 전달 — 네트워크 타임아웃 후 재시도 시 중복 취소 방지.
     * TODO [FUTURE]: 서버 장애(프로세스 종료) 후 PG 미취소 건 복구를 위해 Outbox 패턴 + 워커 재시도 고려.
     */
    @Transactional
    public RefundDetailResponse approveRefund(Long refundId) {
        // Refund 비관적 락 획득 (동시 승인/거절 방지)
        Refund refund = refundRepository.findByIdWithLock(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // PENDING 사전 체크 — 아래 PaymentOrder DB 쿼리+락 건너뜀 (불필요한 DB 접근 방지)
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }

        // PaymentOrder 비관적 락 획득 (Refund 락 후 획득, 순서 일관성 유지)
        PaymentOrder order = paymentOrderRepository.findByIdWithLock(refund.getPaymentOrderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // order.refund() 먼저 — COMPLETED 가드 수행 후 REFUNDED로 전이
        // 결제 주문 취소 가능 여부를 먼저 확인한 뒤 환불 승인으로 전이하는 것이 도메인 의도에 부합
        order.refund();
        refund.approve();

        // 엽전 회수 + 이력 저장 (잔액 부족 시 PAY_001)
        walletService.reclaimForRefund(refund.getUserId(), refund.getAmount(), refund.getPaymentOrderId());

        // PG 취소 — 트랜잭션 내 실행. 실패 시 위의 모든 DB 변경 롤백
        paymentGatewayClient.cancelPayment(
            order.getPgTransactionId(),
            refund.getAmount(),
            refund.getReason() != null ? refund.getReason() : "관리자 환불 승인"
        );

        return RefundDetailResponse.from(refund);
    }

    /**
     * 환불 거절.
     * Refund 상태 REJECTED로 변경 + 거절 사유 저장. PG 호출 없음.
     */
    @Transactional
    public RefundDetailResponse rejectRefund(Long refundId, String rejectReason) {
        Refund refund = refundRepository.findByIdWithLock(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        refund.reject(rejectReason);

        return RefundDetailResponse.from(refund);
    }

    /**
     * 관리자 환불 목록 cursor 페이징 조회.
     * cursor: CursorUtils — id를 Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<RefundDetailResponse> getRefunds(String cursor, int size) {
        // 페이지 크기 범위 검증
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // size+1개 조회 → hasNext 판단 후 실제 size개만 응답
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = cursor != null ? decodeCursorSafe(cursor) : null;
        List<Refund> refunds = refundRepository.findWithCursor(cursorId, pageable);

        // 다음 페이지 존재 여부 판단 + nextCursor 생성
        boolean hasNext = refunds.size() > size;
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<RefundDetailResponse> responses = content.stream()
            .map(RefundDetailResponse::from)
            .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    // CursorUtils.decode 예외를 INVALID_CURSOR 비즈니스 예외로 변환
    private Long decodeCursorSafe(String cursor) {
        try {
            return CursorUtils.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
