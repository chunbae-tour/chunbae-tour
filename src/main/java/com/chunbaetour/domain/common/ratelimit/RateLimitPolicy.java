package com.chunbaetour.domain.common.ratelimit;

import java.time.Duration;

/**
 * Rate Limit 정책 값 객체.
 *
 * <p>한 endpoint에 적용할 "허용 횟수 + 시간 창" 묶음. 호출자는 본 객체를 {@link RateLimiter#tryConsume}에
 * 전달해 정책 강제를 요청한다.
 *
 * <p>Fixed Window 알고리즘에서 사용되는 의미:
 * <ul>
 *   <li>{@code limit} = 한 window 안에서 허용되는 최대 요청 수</li>
 *   <li>{@code window} = 카운터가 0으로 리셋되는 시간 간격</li>
 * </ul>
 *
 * <p>예시:
 * <ul>
 *   <li>signup: {@code new RateLimitPolicy(3, Duration.ofMinutes(10))} — 10분당 3회</li>
 *   <li>login: {@code new RateLimitPolicy(5, Duration.ofMinutes(1))} — 분당 5회</li>
 * </ul>
 *
 * <p>compact constructor에서 양수 검증을 강제해 yml 설정 오류를 부팅 시점에 차단한다.
 *
 * @param limit  허용 횟수 (1 이상)
 * @param window 시간 창 (양수)
 */
public record RateLimitPolicy(int limit, Duration window) {

    public RateLimitPolicy {
        if (limit <= 0) {
            throw new IllegalArgumentException("Rate limit 'limit'은 1 이상이어야 합니다. 현재: " + limit);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit 'window'는 양수여야 합니다. 현재: " + window);
        }
    }
}
