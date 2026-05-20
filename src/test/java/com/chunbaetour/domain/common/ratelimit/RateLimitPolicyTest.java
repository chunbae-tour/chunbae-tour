package com.chunbaetour.domain.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link RateLimitPolicy} compact constructor 검증 단위 테스트.
 *
 * <p>yml 설정 오류를 부팅 시점에 차단하는 게 본 record의 핵심 역할이므로 모든 거부 분기를 명시적으로 검증한다.
 *
 * <p>특히 <b>window 1초 하한</b>은 Redis EXPIRE 초 단위 절사로 인한 silent fail을 차단하는 보안 정책 강제 — sub-second
 * window가 무심코 yml에 들어가면 rate limit이 무력화되므로 부팅 단계에서 강제 거부한다.
 */
class RateLimitPolicyTest {

    @Test
    void valid_policy_is_constructed() {
        RateLimitPolicy policy = new RateLimitPolicy(5, Duration.ofMinutes(1));

        assertThat(policy.limit()).isEqualTo(5);
        assertThat(policy.window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void min_window_of_one_second_is_accepted() {
        // 정확히 1초 하한은 통과 — 경계값 검증
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofSeconds(1));

        assertThat(policy.window()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void limit_zero_throws() {
        assertThatThrownBy(() -> new RateLimitPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void limit_negative_throws() {
        assertThatThrownBy(() -> new RateLimitPolicy(-1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void window_null_throws() {
        assertThatThrownBy(() -> new RateLimitPolicy(5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    @Test
    void window_zero_throws() {
        assertThatThrownBy(() -> new RateLimitPolicy(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    @Test
    void window_negative_throws() {
        assertThatThrownBy(() -> new RateLimitPolicy(5, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    /**
     * 핵심 회귀 방지: sub-second window는 Redis EXPIRE 절사로 silent fail을 일으키므로 부팅에서 차단되어야 한다.
     */
    @Test
    void window_below_one_second_throws_to_prevent_silent_fail() {
        // 500ms — toSeconds()=0 → EXPIRE 0 → 키 즉시 삭제 → rate limit 무력화 시나리오
        assertThatThrownBy(() -> new RateLimitPolicy(5, Duration.ofMillis(500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1초 이상");
    }

    @Test
    void window_999_millis_throws() {
        // 1초 직전도 거부 — boundary 검증
        assertThatThrownBy(() -> new RateLimitPolicy(5, Duration.ofMillis(999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1초 이상");
    }
}
