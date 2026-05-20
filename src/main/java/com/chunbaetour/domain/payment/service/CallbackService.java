package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallbackService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final IdempotencyService idempotencyService;
    private final WalletService walletService;

    // noRollbackFor: 금액 불일치 시 order.fail() DB 커밋 보장 (throw해도 롤백 안 됨)
    @Transactional(noRollbackFor = PaymentException.class)
    public void handleSuccess(String paymentId, String txId) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            return;
        }

        PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(paymentId);

        if (!info.isPaid() || info.totalAmount() == null || !info.totalAmount().equals(order.getAmount())) {
            order.fail();
            scheduleUnmark(order.getIdempotencyKey());
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        order.complete(txId);
        walletService.charge(order.getUserId(), order.getAmount(), order.getId());
    }

    @Transactional
    public void handleFail(String paymentId) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            return;
        }

        order.fail();
        scheduleUnmark(order.getIdempotencyKey());
    }

    // DB 커밋 성공 후에만 Redis 키 삭제 — 커밋 실패 시 키 보존으로 재시도 가능
    private void scheduleUnmark(String idempotencyKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    idempotencyService.unmark(idempotencyKey);
                }
            });
        } else {
            idempotencyService.unmark(idempotencyKey);
        }
    }
}
