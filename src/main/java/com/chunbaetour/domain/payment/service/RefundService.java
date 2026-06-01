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
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_PERIOD_DAYS = 7;
    private static final String UK_REFUNDS_PAYMENT_ORDER_ID = "uk_refunds_payment_order_id";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        RefundPreparation preparation = transactionTemplate.execute(status ->
                prepareRefund(userId, orderId, request.reason()));

        if (preparation.alreadyApprovedResponse() != null) {
            return preparation.alreadyApprovedResponse();
        }

        try {
            paymentGatewayClient.cancelPayment(preparation.orderUid(), preparation.amount(), preparation.reason());
        } catch (PaymentException e) {
            transactionTemplate.executeWithoutResult(status -> markRefundFailed(preparation.refundId()));
            throw new BusinessException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }

        return transactionTemplate.execute(status -> completeRefundAfterGatewayCancel(preparation));
    }

    private RefundPreparation prepareRefund(Long userId, String orderId, String reason) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        Refund existingRefund = refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(order.getId()).orElse(null);
        if (order.getStatus() == PaymentOrderStatus.REFUNDED) {
            if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
                return RefundPreparation.alreadyApproved(RefundResponse.from(existingRefund));
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        if (order.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        Wallet wallet = walletRepository.findByUserId(order.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < order.getAmount()) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
            return RefundPreparation.alreadyApproved(RefundResponse.from(existingRefund));
        }
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        try {
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), reason);
            refundRepository.saveAndFlush(refund);
            return RefundPreparation.pending(order.getOrderUid(), order.getAmount(), refund.getId(), reason);
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

    private void markRefundFailed(Long refundId) {
        refundRepository.findByIdWithLock(refundId)
                .ifPresent(refund -> {
                    if (refund.getStatus() == RefundStatus.PENDING) {
                        refund.fail("PORTONE_CANCEL_FAILED");
                    }
                });
    }

    private RefundResponse completeRefundAfterGatewayCancel(RefundPreparation preparation) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(preparation.orderUid())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));
        Refund refund = refundRepository.findByIdWithLock(preparation.refundId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        if (refund.getStatus() == RefundStatus.APPROVED) {
            return RefundResponse.from(refund);
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }

        if (order.getStatus() == PaymentOrderStatus.COMPLETED
                || order.getStatus() == PaymentOrderStatus.PARTIAL_CANCELLED) {
            long reclaimed = walletService.reclaimAvailableForRefund(
                    order.getUserId(),
                    order.getAmount(),
                    order.getId()
            );
            if (reclaimed == order.getAmount()) {
                order.refund();
            } else {
                order.requireAdjustment();
            }
        }

        refund.approve();
        return RefundResponse.from(refund);
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

    private record RefundPreparation(
            String orderUid,
            Long amount,
            Long refundId,
            String reason,
            RefundResponse alreadyApprovedResponse
    ) {
        private static RefundPreparation pending(String orderUid, Long amount, Long refundId, String reason) {
            return new RefundPreparation(orderUid, amount, refundId, reason, null);
        }

        private static RefundPreparation alreadyApproved(RefundResponse response) {
            return new RefundPreparation(null, null, null, null, response);
        }
    }
}
