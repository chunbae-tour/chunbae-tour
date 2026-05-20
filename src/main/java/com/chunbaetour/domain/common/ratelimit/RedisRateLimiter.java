package com.chunbaetour.domain.common.ratelimit;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 Rate Limiter — Fixed Window 알고리즘.
 *
 * <p>핵심 설계:
 * <ul>
 *   <li><b>Fixed Window</b>: 첫 요청에서 키 생성 + TTL 부여. 같은 window 안의 후속 요청은 INCR만.
 *       TTL 만료 시 키 자동 삭제 → 다음 요청이 새 window 시작.</li>
 *   <li><b>Lua atomic</b>: INCR + EXPIRE를 한 스크립트로 묶어 race condition 차단.
 *       Redis 단일 스레드 모델 + Lua 실행 중 다른 명령 차단으로 원자성 보장.</li>
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
public class RedisRateLimiter implements RateLimiter {

    /**
     * Fixed Window 카운터 Lua 스크립트.
     *
     * <p>의도:
     * <ul>
     *   <li>키를 INCR (없으면 1로 시작)</li>
     *   <li>카운터가 1이면 (첫 요청) TTL 부여</li>
     *   <li>limit 초과 시 -1 반환 (denied), 그렇지 않으면 remaining 반환</li>
     * </ul>
     *
     * <p>반환:
     * <ul>
     *   <li>{@code -1}: 거부 (한도 초과)</li>
     *   <li>{@code >= 0}: 허용. 값은 본 요청 처리 후 남은 허용 횟수</li>
     * </ul>
     *
     * <p>Redis는 Lua 스크립트 실행 중 다른 명령을 받지 않아 INCR+EXPIRE가 원자적으로 처리된다.
     */
    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local limit = tonumber(ARGV[2])
            if current > limit then
              return -1
            end
            return limit - current
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    @Override
    public RateLimitDecision tryConsume(String key, RateLimitPolicy policy) {
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(key),
                String.valueOf(policy.window().toSeconds()),
                String.valueOf(policy.limit())
        );
        if (result == null) {
            // Redis 응답 누락은 운영상 정책 결정 — 실패 시 차단 (fail-closed) vs 허용 (fail-open).
            // 본 구현은 차단을 택해 보안 정책 강제 (Redis 장애 시 회원가입/로그인 일시 차단되더라도 무차별 공격 방어 우선).
            return RateLimitDecision.denied(policy.window());
        }
        if (result < 0) {
            // 거부: Retry-After = window 잔여 TTL. 정확한 TTL 조회는 추가 라운드트립이 비싸므로
            // window 전체 길이로 근사 (Fixed Window 정책상 최악 시나리오 안내)
            Duration retryAfter = redisTtlOrFallback(key, policy.window());
            return RateLimitDecision.denied(retryAfter);
        }
        return RateLimitDecision.allowed(result);
    }

    /**
     * Redis 키의 남은 TTL을 조회. 키가 만료되어 사라졌거나 TTL 정보가 없으면 정책 window로 fallback.
     *
     * <p>Retry-After 정확도는 운영상 클라이언트 UX에 직접 영향 — 너무 길면 의미 없는 대기 발생.
     * TTL 조회는 INCR과 별개 라운드트립이지만 거부 시점에만 발생하므로 비용 미미.
     */
    private Duration redisTtlOrFallback(String key, Duration fallback) {
        Long ttlSeconds = redis.getExpire(key);
        if (ttlSeconds == null || ttlSeconds < 0) {
            return fallback;
        }
        return Duration.ofSeconds(ttlSeconds);
    }
}
