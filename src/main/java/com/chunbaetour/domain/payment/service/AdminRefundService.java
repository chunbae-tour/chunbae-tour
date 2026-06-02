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
        // 1. Refund 비관적 락 획득 — 동시에 승인/거절/취소 요청이 들어와도 하나만 처리됨
        Refund refund = refundRepository.findByIdWithLock(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 2. PENDING 여부 사전 체크 — 이미 처리된 환불이면 즉시 실패, 불필요한 PaymentOrder 락 획득 생략
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }

        // 3. PaymentOrder 비관적 락 획득 — Refund 락 획득 후 Order 락 획득 (데드락 방지를 위한 락 순서 준수)
        PaymentOrder order = paymentOrderRepository.findByIdWithLock(refund.getPaymentOrderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 4. 결제 주문 상태를 REFUNDED로 전이 — order.refund() 내부에서 COMPLETED 가드 수행 (아니면 예외)
        // order를 먼저 전이해야 "취소 가능한 주문인지" 도메인 레벨 검증 후 환불 승인 진행
        order.refund();
        // 5. 환불 상태를 APPROVED로 전이
        refund.approve();

        // 6. 사용자 잔액에서 환불 금액 차감 + 이력 저장
        // PENDING 중 엽전 소비로 잔액 부족이면 PAY_001 예외 → 트랜잭션 전체 롤백
        walletService.reclaimForRefund(refund.getUserId(), refund.getAmount(), refund.getPaymentOrderId());

        // 7. PG에 결제 취소 요청 — 실패 시 예외 전파 → order/refund/wallet 모두 롤백
        // PG 호출을 마지막에 두는 이유: DB가 커밋 준비된 상태에서 외부 호출해야 재시도 시 멱등 복구 가능
        // PortOne V2 취소 API: POST /payments/{paymentId}/cancel — path에는 transactionId가 아닌 paymentId(=orderUid) 사용
        paymentGatewayClient.cancelPayment(
            order.getOrderUid(),
            refund.getAmount(),
            refund.getReason() != null ? refund.getReason() : "관리자 환불 승인",
            "refund-" + refund.getId()
        );
        // 승인 완료된 환불 정보를 DTO로 변환해서 반환
        return RefundDetailResponse.from(refund);
    }

    /**
     * 환불 거절.
     * Refund 상태 REJECTED로 변경 + 거절 사유 저장. PG 호출 없음.
     */
    @Transactional
    public RefundDetailResponse rejectRefund(Long refundId, String rejectReason) {
        // 거절 처리 중 동시 승인/취소 방지 — 비관적 락으로 단독 접근 보장
        Refund refund = refundRepository.findByIdWithLock(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // PENDING → REJECTED 상태 전이 + 거절 사유 저장 — 실제 환불이 아니므로 PG 취소·PaymentOrder 변경 없음
        refund.reject(rejectReason);
        // 거절된 환불 정보를 DTO로 변환해서 반환
        return RefundDetailResponse.from(refund);
    }

    /**
     * 관리자 환불 목록 cursor 페이징 조회.
     * cursor: CursorUtils — id를 Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<RefundDetailResponse> getRefunds(String cursor, int size) {
        // size+1을 DB에 요청 — 마지막 원소 존재 여부로 다음 페이지 판단
        PageRequest pageable = PageRequest.of(0, size + 1);
        // cursor가 있으면 디코딩해서 마지막으로 받은 id 추출, 없으면 null(첫 페이지)
        Long cursorId = CursorUtils.decodeSafe(cursor);
        // cursorId null이면 전체 첫 페이지, 값 있으면 해당 id 이전 데이터 조회
        List<Refund> refunds = refundRepository.findWithCursor(cursorId, pageable);

        // size+1개 왔으면 다음 페이지 존재
        boolean hasNext = refunds.size() > size;
        // 실제 응답은 size개만 — size+1번째 원소는 hasNext 판단 후 제거
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        // 다음 페이지 cursor — 마지막 원소의 id를 Base64URL로 인코딩해서 클라이언트에 전달
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        // Refund 엔티티를 관리자용 응답 DTO로 변환
        List<RefundDetailResponse> responses = content.stream()
            .map(RefundDetailResponse::from)
            .toList();
        // cursor 페이지 응답 조립 후 반환
        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

}
