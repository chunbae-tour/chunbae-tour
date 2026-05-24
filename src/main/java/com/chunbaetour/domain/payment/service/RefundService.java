package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 요청 서비스 (STORY-06, KAN-115).
 * 유저가 환불을 요청하면 Refund 엔티티를 PENDING으로 생성.
 * 실제 PG 환불은 STORY-07 관리자 승인 시점에 수행.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_PERIOD_DAYS = 7;
    private static final String UK_REFUNDS_PAYMENT_ORDER_ID = "uk_refunds_payment_order_id";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;

    /**
     * 환불 요청 생성.
     * orderId = PaymentOrder.orderUid (UUID 문자열, 충전 응답으로 전달받은 값).
     *
     * <p>검증 순서:
     * 1. 주문 존재 확인 → PAY_009
     * 2. 본인 주문 확인 → PAY_011
     * 3. COMPLETED 상태 확인 → PAY_015
     * 4. 환불 기간(7일) 확인 → PAY_010
     * 5. 잔액 전액 보유 확인 → PAY_017 (부분 사용 후 환불 불가)
     * 6. 중복 환불 요청 확인 → PAY_016
     *
     * <p><b>잔액 검증 주의:</b> 본 메서드는 요청 시점 잔액만 검증한다.
     * PENDING 상태에서 사용자가 엽전을 추가 소비하면 잔액이 줄 수 있으므로,
     * 관리자 승인(STORY-07) 시점에 잔액 재검증 + 차감을 원자적으로 수행해야 한다.
     */
    @Transactional
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        // 1. 주문 조회 — orderId는 충전 시 발급된 orderUid(UUID), PK(id)가 아님
        PaymentOrder order = paymentOrderRepository.findByOrderUid(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 2. 본인 주문 확인 — 다른 사용자의 주문에 환불 요청 불가
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // 3. 결제 완료 상태 확인 — PENDING(결제 진행 중)/FAILED/CANCELLED 주문은 환불 대상 아님
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        // 4. 환불 기간(7일) 초과 확인 — 충전일 기준 7일 이내만 환불 허용
        if (order.getCreatedAt().isBefore(LocalDateTime.now().minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // 5. 사용자 지갑 조회 (요청 시점 잔액 1차 검증용)
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        // 엽전을 일부라도 사용했으면 balance < amount → 환불 불가
        // 2차 검증은 관리자 승인 시 WalletService.reclaimForRefund()에서 비관적 락 + 재확인
        if (wallet.getBalance() < order.getAmount()) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        // 6. 동일 주문에 이미 PENDING 환불이 있는지 확인 — 순차적 중복 요청 차단
        // 동시 요청은 아래 saveAndFlush의 UK 제약이 최종 방어
        if (refundRepository.existsByPaymentOrderIdAndStatus(order.getId(), RefundStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        try {
            // 7. 환불 요청 엔티티 생성 (전액 환불, PENDING 상태)
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), request.reason());
            // DB에 즉시 저장 — save()는 트랜잭션 종료 시 flush라 UK 위반을 늦게 감지, saveAndFlush로 즉시 감지
            refundRepository.saveAndFlush(refund);
            // 생성된 환불 요청을 응답 DTO로 변환해서 반환
            return RefundResponse.from(refund);
        } catch (DataIntegrityViolationException e) {
            // UK 제약(uk_refunds_payment_order_id) 위반 시 동시 중복 요청 → DUPLICATE_REFUND_REQUEST로 변환
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UK_REFUNDS_PAYMENT_ORDER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
            }
            // UK 위반 아닌 그 외 DB 예외는 그대로 전파
            throw e;
        }
    }

    /**
     * 사용자 환불 내역 cursor 페이징 조회 (KAN-115).
     * status 파라미터 생략 시 전체 상태 조회.
     * cursor 형식: id(Long)를 Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<UserRefundResponse> getUserRefundHistory(
            Long userId, RefundStatus status, String cursor, int size) {
        // size가 허용 범위(1~100) 벗어나면 즉시 거부 — 과도한 DB 조회 방지
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // size+1을 DB에 요청 — 마지막 원소 존재 여부로 다음 페이지 판단
        PageRequest pageable = PageRequest.of(0, size + 1);
        // cursor가 있으면 Base64URL 디코딩해서 마지막으로 받은 id 추출, 없으면 null(첫 페이지)
        Long cursorId = CursorUtils.decodeSafe(cursor);
        // status/cursorId null이면 조건 미적용 — 4가지 조합(필터유무×cursor유무)을 쿼리 1개로 처리
        List<Refund> refunds = refundRepository.findByUserIdWithFilter(userId, status, cursorId, pageable);

        // size+1개 왔으면 다음 페이지 존재
        boolean hasNext = refunds.size() > size;
        // 실제 응답은 size개만 — size+1번째 원소는 hasNext 판단 후 제거
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        // 다음 페이지 cursor — 마지막 원소 id를 Base64URL로 인코딩해서 클라이언트에 전달
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        // Refund 엔티티를 사용자용 응답 DTO로 변환
        List<UserRefundResponse> responses = content.stream()
                .map(UserRefundResponse::from)
                .toList();

        // cursor 페이지 응답 조립 후 반환
        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /** 환불 요청 취소. PENDING 상태만 취소 가능 → PAY_019. 타인 요청 취소 시 → PAY_011. */
    @Transactional
    public void cancelRefund(Long userId, Long refundId) {
        // 환불 요청 조회 + 비관적 락 획득 — 관리자 approve()와 사용자 cancel()이 동시에 들어와도 하나만 처리
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 본인 환불 요청인지 확인 — 타인 요청 취소 시 FORBIDDEN
        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // PENDING → CANCELLED 상태 전이 — PENDING이 아니면 엔티티 내부에서 PAY_019 throw
        refund.cancel();
    }

}
