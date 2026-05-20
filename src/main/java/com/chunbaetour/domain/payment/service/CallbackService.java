package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.dto.request.WebhookPayload;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.WebhookEventType;
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

    public void handle(String webhookId, WebhookPayload payload) {
        // Standard Webhooks: 동일 webhook-id 재전송 시 즉시 200 리턴 (중복 처리 방지)
        if (!idempotencyService.markWebhookIfAbsent(webhookId)) {
            return;
        }

        if (payload.data() == null) return;

        WebhookEventType eventType = WebhookEventType.from(payload.type());
        switch (eventType) {
            case TRANSACTION_PAID ->
                    handleSuccess(payload.data().paymentId(), payload.data().txId());
            case TRANSACTION_FAILED, TRANSACTION_CANCELLED ->
                    handleFail(payload.data().paymentId());
            default -> { }
        }
    }

    // noRollbackFor: 금액 불일치 시 order.fail() DB 커밋 보장 (throw해도 롤백 안 됨)
    @Transactional(noRollbackFor = PaymentException.class)
    public void handleSuccess(String paymentId, String txId) {
        // 1단계: 락 없이 조회 + PG 검증 (외부 네트워크 호출, 락 미점유)
        PaymentOrder orderForVerify = paymentOrderRepository.findByOrderUid(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // COMPLETED만 조기 리턴: FAILED는 handleFail 선점 경합일 수 있으므로 계속 진행
        if (orderForVerify.getStatus() == PaymentOrderStatus.COMPLETED) {
            return;
        }

        PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(paymentId);
        boolean amountValid = info.isPaid()
                && info.totalAmount() != null
                && info.totalAmount().equals(orderForVerify.getAmount());

        // 2단계: 락 획득 후 상태 변경 (락 점유 시간 최소화)
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
            return;
        }

        if (!amountValid) {
            // PENDING → FAILED 전환, 이미 FAILED면 멱등 처리 (중복 unmark 방지)
            if (order.getStatus() == PaymentOrderStatus.PENDING) {
                order.fail();
                scheduleUnmark(order.getIdempotencyKey());
            }
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // PG PAID 확인: handleFail 선점으로 FAILED 상태여도 PG 결과가 우선 — 결제 유실 방지
        order.complete(txId);
        walletService.charge(order.getUserId(), order.getAmount(), order.getId());
        scheduleUnmark(order.getIdempotencyKey());
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
