package com.chunbaetour.domain.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link RateLimitProperties.EndpointPolicy} compact constructor 검증 단위 테스트.
 *
 * <p>핵심 회귀 방지: yml 바인딩 시점에 limit/window 계약 위반을 차단해야 한다.
 * 과거에는 {@code toPolicy()} 호출(=첫 요청) 시점에 비로소 실패하여 잘못된 yml이 부팅을 통과했다.
 *
 * <p>RateLimitPolicy invariant 자체는 {@link RateLimitPolicyTest}가 모두 커버하므로, 본 테스트는
 * EndpointPolicy 바인딩이 RateLimitPolicy 검증을 실제로 트리거하는지만 증명한다.
 */
class RateLimitPropertiesTest {

    @Test
    void endpoint_policy_with_invalid_limit_fails_at_construction() {
        // limit=0 → RateLimitPolicy compact ctor가 거부해야 함
        assertThatThrownBy(
                        () ->
                                new RateLimitProperties.EndpointPolicy(
                                        "x", "GET", "/x", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endpoint_policy_with_sub_second_window_fails_at_construction() {
        // window=500ms → Redis EXPIRE 절사로 silent fail 방지를 위해 거부해야 함
        assertThatThrownBy(
                        () ->
                                new RateLimitProperties.EndpointPolicy(
                                        "x", "GET", "/x", 5, Duration.ofMillis(500)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
