package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
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
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@TestPropertySource(properties = {
        "portone.secret=test-secret",
        "portone.webhook-secret=test-webhook-secret",
        "portone.store-id=test-store",
        "portone.base-url=http://localhost:9999",
        "portone.channel.card=test-channel-card"
})
class RefundRetrySchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 200L;
    private static final String ORDER_UID = "order-retry-integration-1";
    private static final Long AMOUNT = 10_000L;

    @Autowired private RefundRetryScheduler refundRetryScheduler;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @MockitoBean private PaymentGatewayClient paymentGatewayClient;

    private PaymentOrder savedOrder;

    @BeforeEach
    void setup() {
        // 충전 완료된 사용자: 지갑 잔액 = 충전 금액
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "balance", AMOUNT);
        walletRepository.save(wallet);
        PaymentOrder order = PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, "idem-key-retry", PaymentMethod.CARD, "pg-order-retry");
        order.complete("tx-retry-1");
        savedOrder = paymentOrderRepository.save(order);
    }

    @AfterEach
    void cleanup() {
        yeopjeonHistoryRepository.deleteAll();
        refundRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    private Refund createFailedRefund() {
        Refund refund = Refund.create(savedOrder.getId(), USER_ID, AMOUNT, "user request");
        // 재시도 시각이 이미 도래한 FAILED 환불 생성
        refund.fail("PORTONE_CANCEL_FAILED", LocalDateTime.now(Clock.systemUTC()).minusMinutes(5));
        return refundRepository.save(refund);
    }

    private Refund createPendingRefund() {
        Refund refund = Refund.create(savedOrder.getId(), USER_ID, AMOUNT, "user request");
        return refundRepository.save(refund); // PENDING 상태 저장
    }

    @Test
    @DisplayName("[비동기 처리] PENDING 환불 → 스케줄러 cancelPayment → APPROVED, wallet 차감, order REFUNDED")
    void processPendingRefunds_cancelSucceeds_approvesRefund() {
        Refund refund = createPendingRefund();
        willDoNothing().given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());

        refundRetryScheduler.processPendingRefunds();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        PaymentOrder reloadedOrder = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();

        verify(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(reloadedOrder.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("[비동기 처리] PENDING 환불 cancelPayment 실패 → FAILED 전환, 재시도 스케줄러 대상 등록")
    void processPendingRefunds_cancelFails_marksAsFailed() {
        Refund refund = createPendingRefund();
        willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null));

        refundRetryScheduler.processPendingRefunds();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(reloaded.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("[장애 복구] FAILED 환불 재시도: cancelPayment 성공 → refund APPROVED, wallet 차감, order REFUNDED")
    void retryFailedRefunds_cancelSucceeds_completesRefund() {
        Refund refund = createFailedRefund();
        willDoNothing().given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());

        refundRetryScheduler.retryFailedRefunds();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        PaymentOrder reloadedOrder = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();

        verify(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(reloadedOrder.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("[장애 복구] cancelPayment 실패 + PG CANCELLED → 타임아웃 후 재시도 시 환불 완료 처리")
    void retryFailedRefunds_cancelFails_butPGAlreadyCancelled_completesRefund() {
        // 시나리오: 이전 시도에서 cancelPayment 타임아웃 → PG는 취소 완료, DB는 FAILED.
        // 재시도 시 cancelPayment가 "이미 취소됨" 에러 반환 → verifyPayment CANCELLED → 환불 완료.
        Refund refund = createFailedRefund();
        willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("CANCELLED", AMOUNT, AMOUNT));

        refundRetryScheduler.retryFailedRefunds();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("[재시도 실패] cancelPayment + verifyPayment 모두 실패 → retryCount 증가, FAILED 유지")
    void retryFailedRefunds_allCallsFail_incrementsRetryCount() {
        Refund refund = createFailedRefund();
        willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));

        refundRetryScheduler.retryFailedRefunds();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        // 다음 재시도 시각(5분 후)이 설정됨
        assertThat(reloaded.getNextRetryAt()).isAfter(LocalDateTime.now(Clock.systemUTC()));
    }

    @Test
    @DisplayName("next_retry_at 미도래 건은 스케줄러 대상에서 제외")
    void retryFailedRefunds_futureNextRetryAt_skipsRefund() {
        Refund refund = Refund.create(savedOrder.getId(), USER_ID, AMOUNT, "user request");
        // 5분 후 재시도 예정 — 아직 도래 안 함
        refund.fail("PORTONE_CANCEL_FAILED", LocalDateTime.now(Clock.systemUTC()).plusMinutes(5));
        refundRepository.save(refund);

        refundRetryScheduler.retryFailedRefunds();

        // PG 호출 없어야 함
        verify(paymentGatewayClient, org.mockito.Mockito.never()).cancelPayment(any(), any(), any(), any());
        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    @DisplayName("[동시성] 두 스레드가 같은 PENDING 환불을 동시 처리해도 DB 상태는 1회만 APPROVED 전환, 지갑 이중 차감 없음")
    void processPendingRefunds_concurrentExecution_dbConsistencyGuaranteed() throws Exception {
        // 시나리오: ShedLock이 없는 직접 호출 경로(테스트)에서 두 스레드가 동시 실행됐을 때
        // completeSchedulerRetry의 비관적 락 + APPROVED early-return이 DB 일관성을 보장하는지 검증
        Refund refund = createPendingRefund();
        willDoNothing().given(paymentGatewayClient).cancelPayment(eq(ORDER_UID), eq(AMOUNT), any(), any());

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> f1 = executor.submit(() -> { startLatch.await(); refundRetryScheduler.processPendingRefunds(); return null; });
        Future<?> f2 = executor.submit(() -> { startLatch.await(); refundRetryScheduler.processPendingRefunds(); return null; });

        startLatch.countDown(); // 동시 시작
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Refund reloaded = refundRepository.findById(refund.getId()).orElseThrow();
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(wallet.getBalance()).isZero(); // 이중 차감 없음
    }

    // @Scheduled 자동 실행 방지 + ShedLock 테이블 미존재 문제 해소 (테스트 DB는 create-drop, Flyway 비활성)
    @TestConfiguration
    static class NoAutoSchedulingConfig {

        // ShedLock이 shedlock 테이블에 접근하지 않도록 항상 락 허용하는 no-op 구현
        @Bean
        @Primary
        LockProvider noOpLockProvider() {
            return lockConfiguration -> java.util.Optional.of(() -> {});
        }

        @Bean
        @Primary
        TaskScheduler noOpTaskScheduler() {
            return new NoOpTaskScheduler();
        }

        private static final class NoOpTaskScheduler implements TaskScheduler {

            private static final ScheduledFuture<Object> NOOP = new ScheduledFuture<>() {
                @Override public long getDelay(TimeUnit u) { return 0L; }
                @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
                @Override public boolean cancel(boolean b) { return true; }
                @Override public boolean isCancelled() { return true; }
                @Override public boolean isDone() { return true; }
                @Override public Object get() { return null; }
                @Override public Object get(long t, TimeUnit u) { return null; }
            };

            @Override public Clock getClock() { return Clock.systemDefaultZone(); }
            @Override public ScheduledFuture<?> schedule(Runnable t, Trigger g) { return NOOP; }
            @Override public ScheduledFuture<?> schedule(Runnable t, Instant s) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable t, Instant s, Duration p) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable t, Duration p) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable t, Instant s, Duration d) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable t, Duration d) { return NOOP; }
        }
    }
}
