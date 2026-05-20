package com.chunbaetour.domain.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link RedisRateLimiter} 통합 테스트.
 *
 * <p>Lua 스크립트 + Redis 실제 동작이 핵심이라 Testcontainers Redis 사용. Mocking으로는 atomic 보장 검증 불가.
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>정책 한도까지 통과 + remaining 정확히 감소</li>
 *   <li>한도 초과 시 거부 + Retry-After 합리적 (PTTL 기반 정확값)</li>
 *   <li>window 만료 후 재충전 (카운터 리셋)</li>
 *   <li>동시 호출 시 정확히 limit만큼만 통과 (원자성 — CountDownLatch로 진짜 동시 경쟁)</li>
 * </ul>
 */
@SpringBootTest
class RedisRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 도메인 prefix {@code ratelimit:*} 키 정리. JVM 단위 공유 컨테이너라 필수.
     *
     * <p>{@code KEYS}는 O(N) blocking이라 Redis 단일 스레드 모델에서 다른 명령을 차단한다.
     * SCAN cursor 기반으로 non-blocking 정리. try-with-resources로 cursor 누수 차단.
     */
    @AfterEach
    void cleanup() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("ratelimit:*")
                .count(100)
                .build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void tryConsume_within_limit_returns_allowed_with_decreasing_remaining() {
        RateLimitPolicy policy = new RateLimitPolicy(3, Duration.ofMinutes(5));
        String key = "ratelimit:test1:" + UUID.randomUUID();

        RateLimitDecision first = rateLimiter.tryConsume(key, policy);
        RateLimitDecision second = rateLimiter.tryConsume(key, policy);
        RateLimitDecision third = rateLimiter.tryConsume(key, policy);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(2);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(1);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isEqualTo(0);
    }

    @Test
    void tryConsume_exceeding_limit_returns_denied_with_accurate_retry_after() {
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofMinutes(5));
        String key = "ratelimit:test2:" + UUID.randomUUID();

        rateLimiter.tryConsume(key, policy);
        rateLimiter.tryConsume(key, policy);
        RateLimitDecision over = rateLimiter.tryConsume(key, policy);

        assertThat(over.allowed()).isFalse();
        assertThat(over.remaining()).isZero();
        // Retry-After는 Lua가 한 번에 반환한 PTTL 기반 — 정책 window 이하의 정확한 값이어야 합리.
        // 별도 getExpire 라운드트립이 없으므로 키 만료 race에 영향받지 않음.
        assertThat(over.retryAfter()).isLessThanOrEqualTo(policy.window());
        assertThat(over.retryAfter()).isPositive();
    }

    @Test
    void tryConsume_after_window_expiration_resets_counter() {
        // 짧은 window로 만료 후 재충전 검증
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofSeconds(1));
        String key = "ratelimit:test3:" + UUID.randomUUID();

        // 첫 요청 허용 → 두 번째 즉시 거부
        assertThat(rateLimiter.tryConsume(key, policy).allowed()).isTrue();
        assertThat(rateLimiter.tryConsume(key, policy).allowed()).isFalse();

        // window 만료 + Redis active expiration 안전 마진. Awaitility로 polling 검증
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    RateLimitDecision afterReset = rateLimiter.tryConsume(key, policy);
                    assertThat(afterReset.allowed()).isTrue();
                });
    }

    @Test
    void tryConsume_with_different_keys_isolates_counters() {
        // 다른 키는 서로 영향 없음 (endpoint별 / IP별 격리)
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofMinutes(5));
        String keyA = "ratelimit:isolated-a:" + UUID.randomUUID();
        String keyB = "ratelimit:isolated-b:" + UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(keyA, policy).allowed()).isTrue();
        assertThat(rateLimiter.tryConsume(keyA, policy).allowed()).isFalse();

        // keyB는 keyA와 무관해야
        assertThat(rateLimiter.tryConsume(keyB, policy).allowed()).isTrue();
    }

    /**
     * 동시성 검증: 10개 스레드가 limit=5 정책으로 <b>진짜 동시에</b> 호출 → 정확히 5번만 통과.
     *
     * <p>CountDownLatch start gate로 모든 스레드를 한 줄에 세운 다음 동시에 해제 → 순차 실행이 아닌 실제
     * race 발생. Lua atomic이 깨지면 5개 초과 통과 가능 — 정책 강제 실패. PRD 핵심 요구사항.
     */
    @Test
    void concurrent_tryConsume_strictly_limits_to_policy() throws Exception {
        RateLimitPolicy policy = new RateLimitPolicy(5, Duration.ofMinutes(5));
        String key = "ratelimit:concurrent:" + UUID.randomUUID();
        int threadCount = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        // 모든 스레드가 startGate에서 대기 → 동시 해제로 진짜 race 발생
                        startGate.await();
                        if (rateLimiter.tryConsume(key, policy).allowed()) {
                            allowedCount.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            boolean completed = doneGate.await(5, TimeUnit.SECONDS);
            assertThat(completed).as("모든 스레드가 시간 안에 끝나야 함").isTrue();

            // Lua atomic 보장 시 정확히 limit개만 통과
            assertThat(allowedCount.get()).isEqualTo(policy.limit());
        } finally {
            // 모든 task 정상 완료 후 graceful shutdown — shutdownNow는 인터럽트가 의미 있을 때만
            pool.shutdown();
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
