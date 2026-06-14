package com.chunbaetour.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PendingOrderReconcileSchedulerTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentGatewayClient paymentGatewayClient;
    @Mock private CallbackService callbackService;
    // 실제 Clock(systemUTC 모사) — 스케줄러가 clock.withZone(KST)를 호출하므로 mock 대신 fixed 사용.
    // 2026-06-02T12:00:00Z = KST 2026-06-02 21:00 → now(clock.withZone(KST)) = NOW.
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private PendingOrderReconcileScheduler scheduler;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 21, 0);

    /** createdAt을 지정한 PENDING 주문 — 스케줄러 stale 조회 결과로 직접 공급. */
    private PaymentOrder pendingOrder(String orderUid, LocalDateTime createdAt) {
        PaymentOrder order = PaymentOrder.create(orderUid, 1L, 10_000L, "idem-" + orderUid, PaymentMethod.CARD, "pg-" + orderUid);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }

    private void givenStale(PaymentOrder... orders) {
        given(paymentOrderRepository.findStalePendingOrders(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(orders));
    }

    @Test
    @DisplayName("PG가 PAID면 handleSuccess로 복구하고 transactionId를 전달한다")
    void paid_recoversViaHandleSuccess() {
        PaymentOrder order = pendingOrder("uid-1", NOW.minusMinutes(15));
        givenStale(order);
        given(paymentGatewayClient.verifyPayment("uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null, "tx-1"));

        scheduler.reconcileStalePendingOrders();

        verify(callbackService).handleSuccess("uid-1", "tx-1");
        verify(callbackService, never()).handleFail(anyString());
    }

    @Test
    @DisplayName("PG가 FAILED면 handleFail로 정리한다")
    void failed_cleansUpViaHandleFail() {
        PaymentOrder order = pendingOrder("uid-2", NOW.minusMinutes(15));
        givenStale(order);
        given(paymentGatewayClient.verifyPayment("uid-2"))
                .willReturn(new PortOnePaymentInfo("FAILED", 10_000L, null, null));

        scheduler.reconcileStalePendingOrders();

        verify(callbackService).handleFail("uid-2");
        verify(callbackService, never()).handleSuccess(anyString(), anyString());
    }

    @Test
    @DisplayName("PG가 CANCELLED면 handleFail로 정리한다")
    void cancelled_cleansUpViaHandleFail() {
        PaymentOrder order = pendingOrder("uid-3", NOW.minusMinutes(15));
        givenStale(order);
        given(paymentGatewayClient.verifyPayment("uid-3"))
                .willReturn(new PortOnePaymentInfo("CANCELLED", 10_000L, 10_000L, null));

        scheduler.reconcileStalePendingOrders();

        verify(callbackService).handleFail("uid-3");
    }

    @Test
    @DisplayName("PG 미결제(READY)이고 이탈 임계(30분) 전이면 보류 — 아무 전이도 하지 않는다")
    void readyButRecent_skips() {
        // createdAt = now-15분 → ABANDON_AGE(30분) 미도달
        PaymentOrder order = pendingOrder("uid-4", NOW.minusMinutes(15));
        givenStale(order);
        given(paymentGatewayClient.verifyPayment("uid-4"))
                .willReturn(new PortOnePaymentInfo("READY", 10_000L, null, null));

        scheduler.reconcileStalePendingOrders();

        verify(callbackService, never()).handleSuccess(anyString(), anyString());
        verify(callbackService, never()).handleFail(anyString());
    }

    @Test
    @DisplayName("PG 미결제(READY)이고 이탈 임계(30분) 초과면 handleFail로 정리한다")
    void readyAndAbandoned_cleansUpViaHandleFail() {
        // createdAt = now-40분 → ABANDON_AGE(30분) 초과
        PaymentOrder order = pendingOrder("uid-5", NOW.minusMinutes(40));
        givenStale(order);
        given(paymentGatewayClient.verifyPayment("uid-5"))
                .willReturn(new PortOnePaymentInfo("READY", 10_000L, null, null));

        scheduler.reconcileStalePendingOrders();

        verify(callbackService).handleFail("uid-5");
    }

    @Test
    @DisplayName("한 건 PG 조회 실패해도 예외를 전파하지 않고 나머지 건을 계속 처리한다")
    void singleFailure_doesNotAbortBatch() {
        PaymentOrder bad = pendingOrder("uid-bad", NOW.minusMinutes(15));
        PaymentOrder good = pendingOrder("uid-good", NOW.minusMinutes(15));
        givenStale(bad, good);
        given(paymentGatewayClient.verifyPayment("uid-bad"))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));
        given(paymentGatewayClient.verifyPayment("uid-good"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null, "tx-good"));

        scheduler.reconcileStalePendingOrders();

        // 실패 건은 건너뛰고, 정상 건은 복구됨
        verify(callbackService).handleSuccess("uid-good", "tx-good");
        verify(callbackService, never()).handleFail(anyString());
    }

    @Test
    @DisplayName("대상이 없으면 PG 호출·콜백 없이 종료한다")
    void noStaleOrders_doesNothing() {
        given(paymentOrderRepository.findStalePendingOrders(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of());

        scheduler.reconcileStalePendingOrders();

        verify(paymentGatewayClient, never()).verifyPayment(anyString());
        verify(callbackService, never()).handleSuccess(anyString(), anyString());
        verify(callbackService, never()).handleFail(anyString());
    }
}
