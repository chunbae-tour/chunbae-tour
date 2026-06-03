package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RefundRetrySchedulerTest {

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentGatewayClient paymentGatewayClient;
    @Mock private RefundService refundService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private Clock clock;

    @InjectMocks
    private RefundRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-06-02T12:00:00Z"));
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        // TransactionTemplate: 콜백 즉시 실행
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            inv.<java.util.function.Consumer<?>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Refund failedRefund(Long id, Long paymentOrderId, int retryCount) {
        Refund refund = Refund.create(paymentOrderId, 1L, 10_000L, "user request");
        ReflectionTestUtils.setField(refund, "id", id);
        ReflectionTestUtils.setField(refund, "status", RefundStatus.FAILED);
        ReflectionTestUtils.setField(refund, "retryCount", retryCount);
        ReflectionTestUtils.setField(refund, "nextRetryAt", LocalDateTime.now().minusMinutes(1));
        return refund;
    }

    private PaymentOrder completedOrder(Long id, String orderUid) {
        PaymentOrder order = PaymentOrder.create(orderUid, 1L, 10_000L, "idem-key", PaymentMethod.CARD, "pg-order");
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        return order;
    }

    private Refund pendingRefund(Long id, Long paymentOrderId) {
        Refund refund = Refund.create(paymentOrderId, 1L, 10_000L, "user request");
        ReflectionTestUtils.setField(refund, "id", id);
        // PENDING은 기본 상태 (Refund.create가 PENDING으로 생성)
        return refund;
    }

    @Test
    @DisplayName("PENDING 환불 처리: cancelPayment 성공 → completeSchedulerRetry 호출")
    void processPendingRefunds_cancelSucceeds_completesRefund() {
        Refund refund = pendingRefund(1L, 100L);
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findByStatusOrderByCreatedAt(eq(RefundStatus.PENDING), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));

        scheduler.processPendingRefunds();

        verify(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        verify(refundService).completeSchedulerRetry(1L, "order-uid-1", 10_000L);
    }

    @Test
    @DisplayName("PENDING 환불 처리 실패 → FAILED 전환 (첫 재시도 1분 후 예약)")
    void processPendingRefunds_cancelFails_marksAsFailed() {
        Refund refund = pendingRefund(1L, 100L);
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findByStatusOrderByCreatedAt(eq(RefundStatus.PENDING), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));
        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        org.mockito.Mockito.doThrow(new com.chunbaetour.domain.payment.exception.PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));

        scheduler.processPendingRefunds();

        verify(refundService, never()).completeSchedulerRetry(any(), any(), any());
        // PENDING → FAILED 전환
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 대상 없으면 PG 호출 없음")
    void retryFailedRefunds_noRetryable_doesNothing() {
        given(refundRepository.findRetryableRefunds(any(), eq(5), eq(RefundStatus.FAILED), any(PageRequest.class)))
                .willReturn(List.of());

        scheduler.retryFailedRefunds();

        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelPayment 성공 → completeSchedulerRetry 호출")
    void retryFailedRefunds_cancelSucceeds_completesRefund() {
        Refund refund = failedRefund(1L, 100L, 0);
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findRetryableRefunds(any(), eq(5), eq(RefundStatus.FAILED), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));

        scheduler.retryFailedRefunds();

        verify(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        verify(refundService).completeSchedulerRetry(1L, "order-uid-1", 10_000L);
    }

    @Test
    @DisplayName("cancelPayment 실패 + PG CANCELLED → completeSchedulerRetry 호출 (타임아웃 후 재시도)")
    void retryFailedRefunds_cancelFails_butPGCancelled_completesRefund() {
        Refund refund = failedRefund(1L, 100L, 0);
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findRetryableRefunds(any(), eq(5), eq(RefundStatus.FAILED), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("CANCELLED", 10_000L, 10_000L));

        scheduler.retryFailedRefunds();

        verify(refundService).completeSchedulerRetry(1L, "order-uid-1", 10_000L);
    }

    @Test
    @DisplayName("cancelPayment 실패 + PG PAID → retryCount 증가, nextRetryAt 갱신")
    void retryFailedRefunds_cancelFails_pgStillPaid_recordsFailure() {
        Refund refund = failedRefund(1L, 100L, 0);
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findRetryableRefunds(any(), eq(5), eq(RefundStatus.FAILED), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));
        // recordFailure가 DB에서 refund를 다시 로드하므로 stub 필요
        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        org.mockito.Mockito.doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));

        scheduler.retryFailedRefunds();

        verify(refundService, never()).completeSchedulerRetry(any(), any(), any());
        assertThat(refund.getRetryCount()).isEqualTo(1);
        assertThat(refund.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("retryCount MAX 도달 시 → REQUIRES_ADMIN 전환 (자동 처리 불가, 관리자 직접 확인)")
    void retryFailedRefunds_maxRetryReached_transitionsToRequiresAdmin() {
        Refund refund = failedRefund(1L, 100L, 4); // 4회 실패, 5번째(마지막) 시도
        PaymentOrder order = completedOrder(100L, "order-uid-1");
        given(refundRepository.findRetryableRefunds(any(), eq(5), eq(RefundStatus.FAILED), any(PageRequest.class)))
                .willReturn(List.of(refund));
        given(paymentOrderRepository.findById(100L)).willReturn(Optional.of(order));
        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        org.mockito.Mockito.doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(eq("order-uid-1"), eq(10_000L), eq("user request"), eq("refund-1"));
        given(paymentGatewayClient.verifyPayment("order-uid-1"))
                .willReturn(new PortOnePaymentInfo("PAID", 10_000L, null));

        scheduler.retryFailedRefunds();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUIRES_ADMIN);
        assertThat(refund.getNextRetryAt()).isNull();
    }
}
