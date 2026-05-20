package com.chunbaetour.domain.common.ratelimit;

import java.time.Duration;

/**
 * Rate Limit 판정 결과.
 *
 * <p>{@link RateLimiter#tryConsume} 호출 결과로 반환된다. 호출자는 {@link #allowed}로 분기 후
 * 거부 시 {@link #retryAfter}를 Retry-After 헤더에 실어 응답한다.
 *
 * <p>응답 헤더 매핑 (RateLimitFilter에서 사용):
 * <ul>
 *   <li>{@code X-RateLimit-Limit}: 정책의 한도</li>
 *   <li>{@code X-RateLimit-Remaining}: {@link #remaining}</li>
 *   <li>{@code Retry-After}: {@link #retryAfter}의 초 단위 값 (REST 표준)</li>
 * </ul>
 *
 * @param allowed    true면 허용, false면 한도 초과로 거부
 * @param remaining  본 요청을 처리한 후 남은 허용 횟수 (거부 시 0)
 * @param retryAfter 거부 시 클라이언트가 재시도하기까지 기다려야 하는 시간 (허용 시 {@link Duration#ZERO})
 */
public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter) {

    /**
     * 인자 검증. 잘못된 도메인 값이 응답 헤더로 흘러나가지 않도록 부팅 시점에 강제 차단.
     */
    public RateLimitDecision {
        if (remaining < 0) {
            // 응답 헤더 X-RateLimit-Remaining에 음수가 노출되면 클라이언트 혼란 + 보안 신호 약화.
            throw new IllegalArgumentException("remaining은 0 이상이어야 합니다. 현재: " + remaining);
        }
        if (retryAfter == null) {
            throw new IllegalArgumentException("retryAfter는 null일 수 없습니다.");
        }
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter는 음수일 수 없습니다. 현재: " + retryAfter);
        }
    }

    /**
     * 허용 결과 단축 생성자. retryAfter는 자동으로 ZERO.
     */
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, remaining, Duration.ZERO);
    }

    /**
     * 거부 결과 단축 생성자. remaining=0, retryAfter는 호출자가 지정.
     *
     * <p>Fixed Window 알고리즘에서 retryAfter는 일반적으로 현재 window의 잔여 TTL.
     */
    public static RateLimitDecision denied(Duration retryAfter) {
        return new RateLimitDecision(false, 0L, retryAfter);
    }
}
