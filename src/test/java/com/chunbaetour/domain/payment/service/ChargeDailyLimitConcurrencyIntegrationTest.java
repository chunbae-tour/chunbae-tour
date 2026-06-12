package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willDoNothing;

import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 일일 충전 한도의 동시성(TOCTOU) 가드 (KAN-293 리뷰 M2).
 *
 * <p>같은 사용자가 서로 다른 멱등키로 동시에 다수 충전을 요청해도, ChargeService의 사용자 단위 분산락이
 * SUM 조회→PENDING INSERT를 직렬화해 일 한도(50만)를 넘기지 못함을 실DB+실Redis로 검증한다.
 * 락이 없으면 모든 요청이 같은 누적 스냅샷을 읽어 한도를 초과(예: 6×10만=60만)할 수 있다.
 */
@SpringBootTest
class ChargeDailyLimitConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final long USER_ID = 9100L;

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 외부 PG 사전등록은 동시성 검증과 무관하므로 no-op 처리 (실제 PortOne 호출 차단)
    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("동시 6건(각 10만) 충전 — 분산락 직렬화로 한도 내 5건만 통과, 누적 50만 이하")
    void concurrentCharges_serializedByLock_doNotExceedDailyLimit() throws InterruptedException {
        willDoNothing().given(paymentGatewayClient).preRegister(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());

        int threads = 6;            // 6 × 100,000 = 600,000 > 한도 500,000
        long amount = 100_000L;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitExceeded = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // 서로 다른 멱등키로 동시 요청 — checkAndMark는 같은 키만 막으므로 키가 다르면 통과
                    chargeService.charge(USER_ID, "idem-" + UUID.randomUUID(),
                            new ChargeRequest(amount, PaymentMethod.CARD));
                    success.incrementAndGet();
                } catch (PaymentException e) {
                    limitExceeded.incrementAndGet(); // PAY_030 등
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown(); // 동시 출발
        pool.shutdown();
        //noinspection ResultOfMethodCallIgnored
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        // 한도 50만 / 건당 10만 → 정확히 5건 성공, 1건 차단
        assertThat(success.get()).isEqualTo(5);
        assertThat(limitExceeded.get()).isEqualTo(1);

        // DB 누적도 한도 이하 — 초과 PENDING이 새지 않았음
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payment_orders WHERE user_id = ?", Long.class, USER_ID);
        assertThat(total).isLessThanOrEqualTo(500_000L);
    }
}
