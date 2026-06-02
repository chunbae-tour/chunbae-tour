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
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CallbackService 단위 테스트.
 *
 * <p>Issue #114 fix (KAN-141)로 구조 변경: 기존 2-phase 조회(findByOrderUid + findByOrderUidWithLock)에서
 * DB-level 조건부 CAS UPDATE(completeIfNotCompleted / failIfPending)로 전환됨. 본 테스트는 cached entity
 * stale 문제를 회피하기 위한 새 흐름을 단위 수준에서 검증한다.
 *
 * <p>상태 전환 검증은 PaymentOrder 도메인 instance가 service에 의해 직접 수정되지 않으므로
 * (CAS UPDATE는 DB만 변경) repository.completeIfNotCompleted / failIfPending 호출 + 반환값 mock으로 흐름을 검증한다.
 */
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
    @DisplayName("정상 성공 콜백: CAS UPDATE 성공 → 엽전 충전 + 멱등키 해제")
    void handleSuccess_normal_completes_order_and_charges_wallet() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        given(paymentOrderRepository.completeIfNotCompleted("order-uid-1", "tx-1")).willReturn(1);

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(paymentOrderRepository).completeIfNotCompleted("order-uid-1", "tx-1");
        verify(walletService).charge(eq(1L), eq(10_000L), any());
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("이미 COMPLETED 주문 성공 콜백: PG 검증/CAS UPDATE/charge 없음, 멱등키는 정리 (afterCommit 장애 방어)")
    void handleSuccess_already_completed_skips_processing_but_releases_idempotency_key() {
        PaymentOrder order = pendingOrder();
        order.complete("tx-prev");
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(paymentGatewayClient, never()).verifyPayment(anyString());
        verify(paymentOrderRepository, never()).completeIfNotCompleted(anyString(), anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
        // COMPLETED 조기 리턴이어도 이전 afterCommit 장애로 키가 남아있을 수 있으므로 정리
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("[동시성 회귀 가드] CAS UPDATE가 0 반환 → 다른 스레드가 먼저 COMPLETED 전환 → charge 호출 없음")
    void handleSuccess_cas_returns_zero_skips_charge_for_idempotency() {
        // 시나리오: 동시 webhook 2건 중 다른 스레드가 먼저 completeIfNotCompleted 실행 →
        // 본 스레드의 CAS UPDATE는 0 반환 (status가 이미 COMPLETED)
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        given(paymentOrderRepository.completeIfNotCompleted("order-uid-1", "tx-1")).willReturn(0);

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(walletService, never()).charge(anyLong(), anyLong(), any());
        verify(idempotencyService, never()).unmark(anyString());
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
    @DisplayName("PortOne 검증 금액 불일치 → failIfPending CAS → PAY_013 + 멱등키 해제")
    void handleSuccess_amount_mismatch_fails_order_and_throws_PAY_013() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 99_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentOrderRepository).failIfPending("order-uid-1");
        verify(idempotencyService).unmark("idem-key-1");
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("금액 불일치 + failIfPending CAS=0 (이미 종착 상태) → 조용히 return (중복 unmark + 불필요 throw 차단)")
    void handleSuccess_amount_mismatch_with_already_failed_returns_silently() {
        // 시나리오: 1단계 조기 리턴이 stale 캐시로 skip된 상태에서 amountValid=false 진입.
        // failIfPending이 0 반환 = 다른 스레드가 이미 종착 상태(COMPLETED/FAILED/REFUNDED) 전환 완료.
        // 본 호출은 중복 처리이므로 throw 시 PortOne 재시도 + 운영 에러 로그 누적 noise — 조용히 return.
        // PR #198 lim-haeun 리뷰 반영.
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 99_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(0);

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(idempotencyService, never()).unmark(anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("transactionId null → amountValid=false → PAY_013 (failIfPending=1 시 정상 unmark)")
    void handleSuccess_null_txId_fails_order_and_throws_PAY_013() {
        // CodeRabbit 지적 반영: PG webhook이 transactionId 누락한 비정상 payload 전송 시,
        // walletService.charge가 빈 transaction id로 실행되는 것을 차단.
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", null))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentOrderRepository, never()).completeIfNotCompleted(anyString(), anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("transactionId blank(빈 문자열) → amountValid=false → PAY_013")
    void handleSuccess_blank_txId_fails_order_and_throws_PAY_013() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "   "))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentOrderRepository, never()).completeIfNotCompleted(anyString(), anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 결제 상태 PAID 아님 → failIfPending CAS → PAY_013 + 멱등키 해제")
    void handleSuccess_not_paid_status_fails_order_and_throws_PAY_013() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("FAILED", 10_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("실패 콜백 정상: PENDING 상태 → failIfPending CAS → 멱등키 해제")
    void handleFail_normal_fails_order_and_unmarks_key() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);

        callbackService.handleFail("order-uid-1");

        verify(paymentOrderRepository).failIfPending("order-uid-1");
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("이미 처리된 주문 실패 콜백은 멱등 처리 (CAS=0 → unmark 없음)")
    void handleFail_already_processed_returns_silently() {
        PaymentOrder order = pendingOrder();
        order.fail();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(0);

        callbackService.handleFail("order-uid-1");

        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("존재하지 않는 주문 실패 콜백 → PAY_009")
    void handleFail_order_not_found_throws_PAY_009() {
        given(paymentOrderRepository.findByOrderUid("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> callbackService.handleFail("unknown"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("Transaction.Cancelled: COMPLETED 주문은 지급 엽전을 회수하고 CANCELLED 처리한다")
    void handleCancelled_completed_order_reclaims_wallet_and_cancels_order() {
        PaymentOrder order = pendingOrder();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.complete("tx-1");
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(walletService.reclaimAvailableForExternalCancel(1L, 10_000L, 100L)).willReturn(10_000L);

        callbackService.handleCancelled("order-uid-1");

        assertThat(order.getStatus()).isEqualTo(com.chunbaetour.domain.payment.type.PaymentOrderStatus.CANCELLED);
        verify(walletService).reclaimAvailableForExternalCancel(1L, 10_000L, 100L);
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("Transaction.Cancelled: 회수 가능한 잔액이 부족하면 음수 잔액 없이 회수필요 상태로 남긴다")
    void handleCancelled_insufficient_balance_marks_adjustment_required() {
        PaymentOrder order = pendingOrder();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.complete("tx-1");
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(walletService.reclaimAvailableForExternalCancel(1L, 10_000L, 100L)).willReturn(3_000L);

        callbackService.handleCancelled("order-uid-1");

        assertThat(order.getStatus()).isEqualTo(com.chunbaetour.domain.payment.type.PaymentOrderStatus.ADJUSTMENT_REQUIRED);
        verify(walletService).reclaimAvailableForExternalCancel(1L, 10_000L, 100L);
    }

    @Test
    @DisplayName("Transaction.Cancelled: 이미 취소된 주문은 멱등하게 처리한다")
    void handleCancelled_already_cancelled_returns_silently() {
        PaymentOrder order = pendingOrder();
        order.cancel();
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handleCancelled("order-uid-1");

        verify(walletService, never()).reclaimAvailableForExternalCancel(anyLong(), anyLong(), anyLong());
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
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(pendingOrder()));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 99_000L, null));
        given(paymentOrderRepository.failIfPending("order-uid-1")).willReturn(1);
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        assertThatThrownBy(() -> callbackService.handle("wh-mismatch", payload))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(idempotencyService, never()).unmarkWebhook(anyString());
    }

    @Test
    @DisplayName("handle(): DataAccessException 등 예상 외 예외 시 webhook-id 키 해제 → 재시도 허용")
    void handle_unexpected_runtime_exception_unmarks_webhook_for_retry() {
        given(idempotencyService.markWebhookIfAbsent("wh-err")).willReturn(true);
        given(paymentOrderRepository.findByOrderUid("order-uid-1"))
                .willThrow(new RuntimeException("DB connection lost"));
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        assertThatThrownBy(() -> callbackService.handle("wh-err", payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection lost");

        verify(idempotencyService).unmarkWebhook("wh-err");
    }

    @Test
    @DisplayName("handle(): 신규 webhook-id → 정상 처리 위임")
    void handle_new_webhookId_delegates_to_handler() {
        given(idempotencyService.markWebhookIfAbsent("wh-new")).willReturn(true);
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(pendingOrder()));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        given(paymentOrderRepository.completeIfNotCompleted("order-uid-1", "tx-1")).willReturn(1);
        WebhookPayload payload = new WebhookPayload("Transaction.Paid",
                new WebhookPayload.WebhookData("order-uid-1", "tx-1"));

        callbackService.handle("wh-new", payload);

        verify(paymentOrderRepository).completeIfNotCompleted("order-uid-1", "tx-1");
        verify(walletService).charge(eq(1L), eq(10_000L), any());
    }

    @Test
    @DisplayName("[경합 회귀] handleFail 선점으로 status=FAILED여도 PG PAID면 CAS UPDATE 허용 → 결제 복구")
    void handleSuccess_recovers_payment_when_handleFail_raced_before_cas() {
        // CAS UPDATE 조건이 "status <> COMPLETED" 이므로 FAILED 상태에서도 COMPLETED 전환 허용.
        // 새 구조는 phase1/phase2 cached entity 구분 없이 read는 단일 — 시나리오 단순화됨.
        // findByOrderUid 시점에 PENDING으로 읽혔다고 가정 (캐시 stale 무관 — CAS는 DB 현재 상태 기준).
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));
        // handleFail이 read와 CAS 사이에 commit되어 row가 FAILED여도, CAS 조건 "status <> COMPLETED" 통과 → 1 반환
        given(paymentOrderRepository.completeIfNotCompleted("order-uid-1", "tx-1")).willReturn(1);

        callbackService.handleSuccess("order-uid-1", "tx-1");

        verify(walletService).charge(eq(1L), eq(10_000L), any());
        verify(idempotencyService).unmark("idem-key-1");
    }

    @Test
    @DisplayName("Transaction.PartialCancelled: COMPLETED 주문에서 취소 금액 전액 회수 → PARTIAL_CANCELLED")
    void handlePartialCancelled_completed_order_full_reclaim_marks_partial_cancelled() {
        PaymentOrder order = pendingOrder();
        ReflectionTestUtils.setField(order, "id", 200L);
        order.complete("tx-1");
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PARTIAL_CANCELLED", 10_000L, 3_000L));
        given(walletService.reclaimAvailableForExternalCancel(1L, 3_000L, 200L)).willReturn(3_000L);

        callbackService.handlePartialCancelled("order-uid-1");

        assertThat(order.getStatus())
                .isEqualTo(com.chunbaetour.domain.payment.type.PaymentOrderStatus.PARTIAL_CANCELLED);
        verify(walletService).reclaimAvailableForExternalCancel(1L, 3_000L, 200L);
    }

    @Test
    @DisplayName("Transaction.PartialCancelled: 잔액 부족으로 일부만 회수 → ADJUSTMENT_REQUIRED")
    void handlePartialCancelled_insufficient_balance_marks_adjustment_required() {
        PaymentOrder order = pendingOrder();
        ReflectionTestUtils.setField(order, "id", 200L);
        order.complete("tx-1");
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PARTIAL_CANCELLED", 10_000L, 3_000L));
        given(walletService.reclaimAvailableForExternalCancel(1L, 3_000L, 200L)).willReturn(1_000L);

        callbackService.handlePartialCancelled("order-uid-1");

        assertThat(order.getStatus())
                .isEqualTo(com.chunbaetour.domain.payment.type.PaymentOrderStatus.ADJUSTMENT_REQUIRED);
    }

    @Test
    @DisplayName("Transaction.PartialCancelled: PG cancelledAmount 0 → ADJUSTMENT_REQUIRED (비정상 payload 방어)")
    void handlePartialCancelled_zero_cancelled_amount_marks_adjustment_required() {
        PaymentOrder order = pendingOrder();
        order.complete("tx-1");
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PARTIAL_CANCELLED", 10_000L, 0L));

        callbackService.handlePartialCancelled("order-uid-1");

        assertThat(order.getStatus())
                .isEqualTo(com.chunbaetour.domain.payment.type.PaymentOrderStatus.ADJUSTMENT_REQUIRED);
        verify(walletService, never()).reclaimAvailableForExternalCancel(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Transaction.PartialCancelled: 이미 PARTIAL_CANCELLED → 멱등 처리 (reclaim 없음)")
    void handlePartialCancelled_already_partial_cancelled_returns_silently() {
        PaymentOrder order = pendingOrder();
        order.complete("tx-1");
        order.partialCancel();
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handlePartialCancelled("order-uid-1");

        verify(paymentGatewayClient, never()).verifyPayment(anyString());
        verify(walletService, never()).reclaimAvailableForExternalCancel(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Transaction.PartialCancelled: COMPLETED 아닌 상태(PENDING 등)는 무시")
    void handlePartialCancelled_non_completed_status_is_ignored() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUidWithLock("order-uid-1")).willReturn(Optional.of(order));

        callbackService.handlePartialCancelled("order-uid-1");

        verify(paymentGatewayClient, never()).verifyPayment(anyString());
        verify(walletService, never()).reclaimAvailableForExternalCancel(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("handle(): Transaction.CancelPending 이벤트는 명시적 무시 (repository/wallet 호출 없음)")
    void handle_cancel_pending_event_does_nothing() {
        given(idempotencyService.markWebhookIfAbsent("wh-cancel-pending")).willReturn(true);
        WebhookPayload payload = new WebhookPayload("Transaction.CancelPending",
                new WebhookPayload.WebhookData("order-uid-1", null));

        callbackService.handle("wh-cancel-pending", payload);

        verifyNoInteractions(paymentOrderRepository, paymentGatewayClient, walletService);
        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("PortOne API 장애 → PAY_005, CAS UPDATE 호출 없음, 멱등키 해제 없음")
    void handleSuccess_pg_unavailable_leaves_order_pending() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderUid("order-uid-1")).willReturn(Optional.of(order));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> callbackService.handleSuccess("order-uid-1", "tx-1"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(paymentOrderRepository, never()).completeIfNotCompleted(anyString(), anyString());
        verify(paymentOrderRepository, never()).failIfPending(anyString());
        verify(idempotencyService, never()).unmark(anyString());
        verify(walletService, never()).charge(anyLong(), anyLong(), any());
    }
}
