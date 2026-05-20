package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.dto.request.WebhookPayload;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallbackServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private CallbackService callbackService;

    private PaymentOrder pendingOrder() {
        return PaymentOrder.create("order-uid-1", 1L, 10_000L, "idem-key-1", PaymentMethod.CARD, "pg-order-1");
    }

    @Test
    @DisplayName("정상 성공 콜백: 주문 COMPLETED 전환 + 엽전 충전 호출")
    void handleSuccess_normal_completes_order_and_charges_wallet() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L));

        callbackService.handleSuccess("order-uid-1", "tx-1");

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(order.getPgTransactionId()).isEqualTo("tx-1");
        verify(walletService).charge(eq(1L), eq(10_000L), any());
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("이미 처리된 주문 성공 콜백은 멱등 처리 (조용히 리턴)")
    void handleSuccess_already_processed_returns_silently() {
        PaymentOrder order = pendingOrder();
        order.complete("tx-prev");
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(paymentGatewayClient, never()).verifyPayment(anyString());
        verify(paymentOrderRepository, never()).findByOrderUidWithLock(anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID → PAY_009")
    void handleSuccess_order_not_found_throws_PAY_009() {
        given(paymentOrderRepository.findByOrderUid("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> callbackService.handleSuccess("unknown", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("PortOne 검증 금액 불일치 → PAY_013, 주문 FAILED, 멱등성 키 해제")
    void handleSuccess_amount_mismatch_fails_order_and_throws_PAY_013() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 99_000L));

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        verify(idempotencyService).unmark("idem-key-1");
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 결제 상태 PAID 아님 → PAY_013, 주문 FAILED, 멱등성 키 해제")
    void handleSuccess_not_paid_status_fails_order_and_throws_PAY_013() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("FAILED", 10_000L));

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("실패 콜백 정상: 주문 FAILED 전환 + 멱등성 키 해제")
    void handleFail_normal_fails_order_and_unmarks_key() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handleFail("order-uid-1");

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("이미 처리된 주문 실패 콜백은 멱등 처리 (멱등성 키 해제 없음)")
    void handleFail_already_processed_returns_silently() {
        PaymentOrder order = pendingOrder();
        order.fail();
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handleFail("order-uid-1");

        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("존재하지 않는 주문 실패 콜백 → PAY_009")
    void handleFail_order_not_found_throws_PAY_009() {
        given(paymentOrderRepository.findByOrderUidWithLock("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> callbackService.handleFail("unknown"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("handle(): 중복 webhook-id → 조용히 리턴 (결제 처리 없음)")
    void handle_duplicate_webhookId_returns_silently() {
        given(idempotencyService.markWebhookIfAbsent("wh-dup")).willReturn(false);
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        callbackService.handle("wh-dup", payload);

        verifyNoInteractions(paymentOrderRepository, paymentGatewayClient, walletService);
    }

    @Test
    @DisplayName("handle(): PG 장애 시 webhook-id 키 해제 → PortOne 재시도 시 재처리 가능")
    void handle_pg_unavailable_unmarks_webhook_for_retry() {
        given(idempotencyService.markWebhookIfAbsent("wh-retry")).willReturn(true);
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(pendingOrder()));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        assertThatThrownBy(() -> callbackService.handle("wh-retry", payload))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService).unmarkWebhook("wh-retry");
    }

    @Test
    @DisplayName("handle(): 금액 불일치 시 webhook-id 키 유지 → 동일 webhook-id 재시도 차단")
    void handle_amount_mismatch_keeps_webhook_key() {
        given(idempotencyService.markWebhookIfAbsent("wh-mismatch")).willReturn(true);
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 99_000L));
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        assertThatThrownBy(() -> callbackService.handle("wh-mismatch", payload))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(idempotencyService, never()).unmarkWebhook(anyString());
    }

    @Test
    @DisplayName("handle(): 신규 webhook-id → 정상 처리 위임")
    void handle_new_webhookId_delegates_to_handler() {
        given(idempotencyService.markWebhookIfAbsent("wh-new")).willReturn(true);
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L));
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        callbackService.handle("wh-new", payload);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("[경합 회귀] handleFail 선점으로 phase2=FAILED여도 PG PAID면 COMPLETED 복구")
    void handleSuccess_recovers_payment_when_handleFail_raced_between_phases() {
        PaymentOrder phase1Order = pendingOrder(); // phase1에서 읽힌 PENDING
        PaymentOrder phase2Order = pendingOrder(); // handleFail이 phase1~2 사이에 커밋 → FAILED
        phase2Order.fail();

        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(phase1Order));
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(phase2Order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L));

        callbackService.handleSuccess("order-uid-1", "tx-1");

        assertThat(phase2Order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(phase2Order.getPgTransactionId()).isEqualTo("tx-1");
        verify(walletService).charge(eq(1L), eq(10_000L), any());
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("PortOne API 장애 → PAY_005, 주문 PENDING 유지, 멱등키 해제 없음")
    void handleSuccess_pg_unavailable_leaves_order_pending() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verify(idempotencyService, never()).unmark(anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }
}
