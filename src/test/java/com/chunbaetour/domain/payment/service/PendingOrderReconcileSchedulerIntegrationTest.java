package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "portone.secret=test-secret",
        "portone.webhook-secret=test-webhook-secret",
        "portone.store-id=test-store",
        "portone.base-url=http://localhost:9999",
        "portone.channel.card=test-channel-card"
})
class PendingOrderReconcileSchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 200L;
    private static final Long AMOUNT = 10_000L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private PendingOrderReconcileScheduler scheduler;
    @Autowired private CallbackService callbackService;
    @MockitoBean private PaymentGatewayClient paymentGatewayClient;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private YeopjeonHistoryRepository yeopjeonHistoryRepository;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        walletRepository.save(Wallet.create(USER_ID));
    }

    @AfterEach
    void cleanup() {
        yeopjeonHistoryRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
        var keys = redis.keys("idempotency:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /** PENDING 주문 저장 후 created_at을 KST 기준 분 단위로 backdate — 스케줄러 stale 임계를 넘기기 위함. */
    private void savePendingBackdated(String orderUid, String idemKey, int ageMinutes) {
        paymentOrderRepository.save(
                PaymentOrder.create(orderUid, USER_ID, AMOUNT, idemKey, PaymentMethod.CARD, "pg-" + orderUid));
        redis.opsForValue().set("idempotency:" + idemKey, "1");
        LocalDateTime backdated = LocalDateTime.now(KST).minusMinutes(ageMinutes);
        jdbc.update("UPDATE payment_orders SET created_at = ? WHERE order_uid = ?",
                Timestamp.valueOf(backdated), orderUid);
    }

    @Test
    @DisplayName("PAID 고아 주문: 스케줄러가 COMPLETED 전환 + 지갑 충전 + 엽전 이력 INSERT")
    void reconcile_paidOrphan_completesAndCreditsWallet() {
        savePendingBackdated("uid-paid", "idem-paid", 20); // 20분 경과 → stale(10분) 대상
        given(paymentGatewayClient.verifyPayment("uid-paid"))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null, "tx-paid"));

        scheduler.reconcileStalePendingOrders();

        PaymentOrder order = paymentOrderRepository.findByOrderUid("uid-paid").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(order.getPgTransactionId()).isEqualTo("tx-paid");
        assertThat(walletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isEqualTo(AMOUNT);
        assertThat(yeopjeonHistoryRepository.findAll()).hasSize(1);
        assertThat(redis.hasKey("idempotency:idem-paid")).isFalse();
    }

    @Test
    @DisplayName("미결제(READY) 이탈 주문: 30분 초과 시 FAILED 전환 + 멱등키 해제 (한도 합산 제외)")
    void reconcile_abandonedReady_failsAndReleasesKey() {
        savePendingBackdated("uid-ready", "idem-ready", 40); // 40분 경과 → abandon(30분) 초과
        given(paymentGatewayClient.verifyPayment("uid-ready"))
                .willReturn(new PortOnePaymentInfo("READY", AMOUNT, null, null));

        scheduler.reconcileStalePendingOrders();

        PaymentOrder order = paymentOrderRepository.findByOrderUid("uid-ready").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(walletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isZero();
        assertThat(redis.hasKey("idempotency:idem-ready")).isFalse();
    }

    @Test
    @DisplayName("스케줄러와 웹훅이 같은 PAID 주문 동시 처리: DB CAS로 지갑 정확히 1회만 충전")
    void reconcile_concurrentWithWebhook_creditsExactlyOnce() throws InterruptedException {
        savePendingBackdated("uid-race", "idem-race", 20);
        given(paymentGatewayClient.verifyPayment("uid-race"))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null, "tx-race"));

        CountDownLatch latch = new CountDownLatch(2);
        List<Exception> errors = Collections.synchronizedList(new java.util.ArrayList<>());

        // 스레드 A: 스케줄러 재조정, 스레드 B: 웹훅 직접 수신(handleSuccess)
        Runnable schedulerTask = wrap(() -> scheduler.reconcileStalePendingOrders(), latch, errors);
        Runnable webhookTask = wrap(() -> callbackService.handleSuccess("uid-race", "tx-webhook"), latch, errors);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(schedulerTask);
        executor.submit(webhookTask);
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        PaymentOrder order = paymentOrderRepository.findByOrderUid("uid-race").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        // 충전·이력 정확히 1회 — 중복 적립 없음
        assertThat(walletRepository.findByUserId(USER_ID).orElseThrow().getBalance()).isEqualTo(AMOUNT);
        assertThat(yeopjeonHistoryRepository.findAll()).hasSize(1);
    }

    private Runnable wrap(Runnable action, CountDownLatch latch, List<Exception> errors) {
        return () -> {
            try {
                action.run();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        };
    }

    // @Scheduled 자동 실행 방지 + ShedLock 테이블 미존재 문제 해소 (스케줄러를 테스트에서 직접 호출하므로)
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
