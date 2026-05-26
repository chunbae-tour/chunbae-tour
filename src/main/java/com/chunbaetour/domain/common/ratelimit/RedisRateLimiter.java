package com.chunbaetour.domain.common.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 Rate Limiter — Fixed Window 알고리즘.
 *
 * <p>Redis 장애 시 fail-closed — {@link DataAccessException} catch → {@link RateLimitDecision#denied}.
 *
 * <p>핵심 설계:
 * <ul>
 *   <li><b>Fixed Window</b>: 첫 요청에서 키 생성 + TTL 부여. 같은 window 안의 후속 요청은 INCR만.
 *       TTL 만료 시 키 자동 삭제 → 다음 요청이 새 window 시작.</li>
 *   <li><b>Lua atomic + TTL 동시 반환</b>: INCR + EXPIRE + PTTL을 한 스크립트로 묶어 race condition 차단.
 *       Lua가 결과와 TTL을 한 번에 반환하므로 별도 getExpire 호출이 불필요 → "결과 -1 받고 getExpire 사이에
 *       키가 만료" 경합 제거.</li>
 *   <li><b>의존성 최소화</b>: Bucket4j 라이브러리 도입 없이 Redis만 사용. RefreshTokenStore Lua 패턴과 일관.</li>
 * </ul>
 *
 * <p>알고리즘 트레이드오프 (ADR 참조):
 * <ul>
 *   <li>장점: 단순/빠름. window TTL 단위로 카운터 자동 정리 → 메모리 관리 부담 없음</li>
 *   <li>단점: window boundary 문제 — 예: 0:59에 5회, 1:01에 5회 = 짧은 시간에 10회 가능
 *       (정책상 회원가입 3회/10분, 로그인 5회/분에는 실용적으로 충분)</li>
 *   <li>정확한 Token Bucket이 필요해지면 Bucket4j 또는 Lua HMSET 기반으로 교체 (Epic B S3 부하 측정 결과에 따라)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    /**
     * Redis 장애로 정책 강제가 불가능할 때 클라이언트에 안내할 Retry-After 값.
     *
     * <p>policy.window()를 그대로 쓰면 최대 10분(signup window) 같은 큰 값이 안내되어 UX가 깨진다.
     * 짧은 고정값(30초)으로 안내해 클라이언트의 부드러운 재시도 + 운영 알람 시간 확보를 동시에 달성.
     */
    private static final Duration FAIL_CLOSED_RETRY = Duration.ofSeconds(30);

    /**
     * Fixed Window 카운터 Lua 스크립트.
     *
     * <p>의도:
     * <ul>
     *   <li>키를 INCR (없으면 1로 시작)</li>
     *   <li>카운터가 1이면 (첫 요청) TTL 부여</li>
     *   <li>PTTL로 남은 TTL(밀리초) 조회</li>
     *   <li>{remaining, ttlMillis} 튜플 반환 — denied 시 ttlMillis가 정확한 Retry-After가 되도록</li>
     * </ul>
     *
     * <p>반환:
     * <ul>
     *   <li>{@code [-1, ttlMillis]}: 거부 (한도 초과). ttlMillis는 window 잔여 ms (PTTL 결과: -1=TTL 없음, -2=키 없음).</li>
     *   <li>{@code [remaining, ttlMillis]}: 허용. remaining은 본 요청 처리 후 남은 허용 횟수 (>=0).</li>
     * </ul>
     *
     * <p>Redis는 Lua 스크립트 실행 중 다른 명령을 받지 않아 INCR + EXPIRE + PTTL이 원자적으로 처리된다.
     * 별도 getExpire 호출(이전 구현)은 Lua와의 사이에 키가 만료되는 race가 있었으나, 본 Lua는 한 번에 묶어
     * 해당 race를 제거한다.
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttlMillis = redis.call('PTTL', KEYS[1])
            local limit = tonumber(ARGV[2])
            if current > limit then
              return {-1, ttlMillis}
            end
            return {limit - current, ttlMillis}
            """,
            List.class
    );

    /**
     * KAN-104 메트릭 — Redis 장애로 fail-closed 처리한 빈도.
     * 카탈로그({@code docs/operations/metrics-catalog.md})와 동기. endpoint tag는 미부여
     * (Redis 장애는 endpoint별로 다르지 않음 + RateLimiter 추상화 단순 유지).
     */
    private static final String METRIC_REDIS_FAILURE = "ratelimit.redis.failure.total";

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    @Override
    public RateLimitDecision tryConsume(String key, RateLimitPolicy policy) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                    CONSUME_SCRIPT,
                    List.of(key),
                    String.valueOf(policy.window().toSeconds()),
                    String.valueOf(policy.limit())
            );

            if (result == null || result.size() != 2) {
                // Lua 실행 결과 누락은 비정상 상태 — 정책상 fail-closed 차단 + 짧은 retry 안내.
                meterRegistry.counter(METRIC_REDIS_FAILURE).increment();
                log.warn("Redis rate limit Lua 실행 결과 누락 또는 형식 불일치");
                return RateLimitDecision.denied(FAIL_CLOSED_RETRY);
            }

            long remaining = result.get(0);
            long ttlMillis = result.get(1);

            if (remaining < 0) {
                // 거부 분기: ttlMillis가 정확한 Retry-After.
                Duration retryAfter = retryAfterFromTtl(ttlMillis, policy.window());
                return RateLimitDecision.denied(retryAfter);
            }
            return RateLimitDecision.allowed(remaining);
        } catch (DataAccessException e) {
            // Redis 장애 (연결 실패, 타임아웃, Lua 오류 등) 시 fail-closed 차단.
            // 보안 정책 강제 우선 — Redis 장애 동안 회원가입/로그인이 일시 차단되더라도 무차별 공격 방어가 더 중요.
            // ERROR 레벨로 로그 — 운영 알람 트리거(WARN보다 강한 시급도). 빈번하면 Redis 클러스터/장애 조사 필요.
            meterRegistry.counter(METRIC_REDIS_FAILURE).increment();
            log.error("Redis rate limit 호출 실패; fail-closed로 차단", e);
            return RateLimitDecision.denied(FAIL_CLOSED_RETRY);
        }
    }

    /**
     * Lua가 반환한 PTTL을 기반으로 Retry-After Duration을 결정한다.
     *
     * <p>PTTL 반환 규약 (Redis 공식):
     * <ul>
     *   <li>{@code > 0}: 정상 남은 TTL (밀리초) — 그대로 사용</li>
     *   <li>{@code -1}: 키는 있지만 TTL 미설정 — 비정상 (Lua가 EXPIRE 호출하므로 발생하면 안 됨).
     *       ERROR 로그 + policy window fallback (안전한 최댓값)</li>
     *   <li>{@code -2}: 키 없음 — Lua 실행 중 만료된 매우 드문 경합. ZERO 반환 (즉시 재시도 가능)</li>
     * </ul>
     */
    private Duration retryAfterFromTtl(long ttlMillis, Duration fallback) {
        if (ttlMillis > 0) {
            return Duration.ofMillis(ttlMillis);
        }
        if (ttlMillis == -2) {
            // 키가 Lua 실행 중 만료된 경합 — 다음 요청은 새 window. 즉시 재시도 가능.
            return Duration.ZERO;
        }
        // -1 = 키는 있지만 TTL 미설정. Lua가 첫 요청에 EXPIRE를 걸므로 발생하면 안 됨 → 운영 이상 신호.
        log.error("Redis rate limit 키에 TTL이 없음 — 정책 위반 의심 (Lua 외부에서 키 조작 가능성)");
        return fallback;
    }
}
