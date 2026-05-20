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
 * <p>compact constructor에서 양수 + window 최소 1초 검증을 강제해 yml 설정 오류를 부팅 시점에 차단한다.
 *
 * <p><b>window 1초 하한 사유</b>: Redis {@code EXPIRE} 명령은 초 단위만 받아 {@code toSeconds()}로 절사된다.
 * 500ms 같은 sub-second 값이 들어오면 {@code toSeconds()=0}이 되고 Redis는 0초 TTL을 "키 즉시 삭제"로
 * 처리해 매 요청마다 카운터가 1로 리셋되어 rate limit이 실질적으로 무력화된다. 1초 미만 정책은 정책상
 * 의미가 없고 silent fail 위험이 크므로 부팅 시점에 강제 차단한다.
 *
 * @param limit  허용 횟수 (1 이상)
 * @param window 시간 창 (1초 이상)
 */
public record RateLimitPolicy(int limit, Duration window) {

    /** Redis EXPIRE 초 단위 절사로 인한 silent fail 방지를 위한 최소 window. */
    private static final Duration MIN_WINDOW = Duration.ofSeconds(1);

    public RateLimitPolicy {
        if (limit <= 0) {
            throw new IllegalArgumentException("Rate limit 'limit'은 1 이상이어야 합니다. 현재: " + limit);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit 'window'는 양수여야 합니다. 현재: " + window);
        }
        if (window.compareTo(MIN_WINDOW) < 0) {
            // toSeconds() 절사로 EXPIRE 0이 들어가면 Redis가 키를 즉시 삭제 → rate limit 무력화.
            // sub-second 정책은 의미상으로도 부적절하므로 부팅 차단.
            throw new IllegalArgumentException(
                    "Rate limit 'window'는 1초 이상이어야 합니다 (Redis EXPIRE 초 단위 절사로 sub-second는 silent fail). 현재: " + window);
        }
    }
}
