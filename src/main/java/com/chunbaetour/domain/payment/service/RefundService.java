package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_PERIOD_DAYS = 7;
    private static final String UK_REFUNDS_PAYMENT_ORDER_ID = "uk_refunds_payment_order_id";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;
    private final Clock clock;

    @Transactional(noRollbackFor = BusinessException.class)
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        Refund existingRefund = refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(order.getId()).orElse(null);
        if (order.getStatus() == PaymentOrderStatus.REFUNDED) {
            if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
                return RefundResponse.from(existingRefund);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        if (order.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < order.getAmount() || isChargeUsed(order)) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
            return RefundResponse.from(existingRefund);
        }
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        try {
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), request.reason());
            refundRepository.saveAndFlush(refund);

            try {
                paymentGatewayClient.cancelPayment(order.getOrderUid(), order.getAmount(), request.reason());
            } catch (PaymentException e) {
                refund.fail("PORTONE_CANCEL_FAILED");
                throw new BusinessException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
            }

            order.refund();
            refund.approve();
            walletService.reclaimForRefund(userId, order.getAmount(), order.getId());
            return RefundResponse.from(refund);
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException cve
                        && UK_REFUNDS_PAYMENT_ORDER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
                }
                cause = cause.getCause();
            }
            throw e;
        }
    }

    public CursorPageResponse<UserRefundResponse> getUserRefundHistory(
            Long userId, RefundStatus status, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Refund> refunds = refundRepository.findByUserIdWithFilter(userId, status, cursorId, pageable);

        boolean hasNext = refunds.size() > size;
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<UserRefundResponse> responses = content.stream()
                .map(UserRefundResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    @Transactional
    public void cancelRefund(Long userId, Long refundId) {
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        refund.cancel();
    }

    private boolean isChargeUsed(PaymentOrder order) {
        return yeopjeonHistoryRepository.findFirstByPaymentOrderIdAndTypeOrderByIdAsc(
                        order.getId(),
                        YeopjeonHistoryType.CHARGE
                )
                .map(YeopjeonHistory::getId)
                .map(chargeHistoryId -> yeopjeonHistoryRepository.existsByUserIdAndIdGreaterThanAndTypeIn(
                        order.getUserId(),
                        chargeHistoryId,
                        List.of(YeopjeonHistoryType.PAYMENT)
                ))
                .orElse(false);
    }
}
