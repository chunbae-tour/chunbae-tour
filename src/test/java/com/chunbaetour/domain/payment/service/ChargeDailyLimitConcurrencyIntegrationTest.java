package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willDoNothing;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 *
 * <p><b>타임존 정렬</b>: charge()가 만드는 PENDING의 created_at은 JPA auditing이 JVM 기본 존으로 기록하는
 * wall-clock인데, DailyChargeLimiter는 KST naive 경계로 당일 합산을 조회한다. 운영은 JVM을
 * {@code -Duser.timezone=Asia/Seoul}로 띄워 둘이 일치하지만, CI 러너 기본 존은 UTC라 KST 자정 직후
 * (UTC 15:00~24:00)에 실행되면 created_at(UTC)이 KST 윈도우 밖으로 밀려 합산에서 누락 → 한도가
 * 발동하지 않아 6건 모두 통과한다. 운영과 동일하게 이 테스트 동안 JVM 기본 존을 KST로 고정해
 * created_at과 limiter 경계의 기준 존을 맞춘다 (KAN-293 리뷰 반영, 자정 무렵 회귀 무관 실패 제거).
 */
@SpringBootTest
class ChargeDailyLimitConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final long USER_ID = 9100L;

    private static TimeZone originalTimeZone;

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 외부 PG 사전등록은 동시성 검증과 무관하므로 no-op 처리 (실제 PortOne 호출 차단)
    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    // created_at(auditing, JVM 기본 존)과 limiter의 KST 경계를 같은 존으로 맞춘다 — 운영(Asia/Seoul)과 동일.
    // 테스트 실행은 순차(parallel 미설정)이므로 클래스 종료 후 원복하면 다른 테스트에 새지 않는다.
    @BeforeAll
    static void fixTimeZoneToKst() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

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
        AtomicInteger limitExceeded = new AtomicInteger();       // PAY_030 — 한도 초과로만 차단된 건
        AtomicInteger unexpectedFailures = new AtomicInteger();  // 그 외 모든 실패(락 타임아웃·인터럽트 등) → 즉시 실패 신호

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
                    // 이 테스트의 계약은 "동시성 하에서 정확히 PAY_030으로만 차단"이다.
                    // 락 대기 초과(PAY_008) 등 다른 결제 예외는 한도 검증과 무관하므로 통과시키지 않고 실패로 집계한다.
                    if (e.getErrorCode() == ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED) {
                        limitExceeded.incrementAndGet();
                    } else {
                        unexpectedFailures.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedFailures.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown(); // 동시 출발
        pool.shutdown();
        //noinspection ResultOfMethodCallIgnored
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        // 한도/락 외의 예외는 없어야 한다 — 오탐 통과 방지
        assertThat(unexpectedFailures.get()).isZero();
        // 한도 50만 / 건당 10만 → 정확히 5건 성공, 1건 PAY_030 차단
        assertThat(success.get()).isEqualTo(5);
        assertThat(limitExceeded.get()).isEqualTo(1);

        // DB 누적도 한도 이하 — 초과 PENDING이 새지 않았음 + 성공 건수와 정확히 일치
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payment_orders WHERE user_id = ?", Long.class, USER_ID);
        assertThat(total).isLessThanOrEqualTo(500_000L);
        assertThat(total).isEqualTo((long) success.get() * amount);
    }
}
