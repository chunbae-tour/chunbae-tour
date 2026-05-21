package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 요청 서비스 (STORY-06).
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
     */
    @Transactional
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        // 주문 조회 (orderUid = 충전 시 발급된 UUID)
        PaymentOrder order = paymentOrderRepository.findByOrderUid(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 본인 주문인지 확인
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // COMPLETED 상태만 환불 가능 (PENDING/FAILED/CANCELLED 불가)
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        // 환불 기간(7일) 초과 확인
        if (order.getCreatedAt().isBefore(LocalDateTime.now().minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // 미사용 엽전 전액 보유 확인 (부분 사용 후 환불 불가)
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < order.getAmount()) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        // 동일 주문에 대한 중복 환불 요청 방지
        if (refundRepository.existsByPaymentOrderIdAndStatus(order.getId(), RefundStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        // 환불 요청 생성 (전액 환불, PENDING 상태)
        // uk_refunds_payment_order_id 위반 시 동시 중복 요청 방어
        try {
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), request.reason());
            refundRepository.saveAndFlush(refund);
            return RefundResponse.from(refund);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UK_REFUNDS_PAYMENT_ORDER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
            }
            throw e;
        }
    }

    /** 환불 요청 취소. PENDING 상태만 취소 가능 → PAY_019. 타인 요청 취소 시 → PAY_011. */
    @Transactional
    public void cancelRefund(Long userId, Long refundId) {
        // 환불 요청 조회 (없으면 PAY_018)
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 본인 환불 요청인지 확인
        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // PENDING 상태만 취소 가능 (APPROVED/REJECTED/CANCELLED 불가)
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_CANCEL_NOT_ALLOWED);
        }

        // 상태를 CANCELLED로 전이
        refund.cancel();
    }
}
