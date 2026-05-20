package com.chunbaetour.domain.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link RateLimitDecision} compact constructor 검증 단위 테스트.
 *
 * <p>잘못된 도메인 값(음수 remaining, null/음수 retryAfter)이 응답 헤더로 흘러나가지 않도록
 * 부팅 시점에 강제 차단되는지 회귀 방지.
 */
class RateLimitDecisionTest {

    @Test
    void allowed_with_remaining_zero_is_constructed() {
        RateLimitDecision decision = RateLimitDecision.allowed(0);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void allowed_with_positive_remaining_is_constructed() {
        RateLimitDecision decision = RateLimitDecision.allowed(5);

        assertThat(decision.remaining()).isEqualTo(5);
    }

    @Test
    void allowed_with_negative_remaining_throws() {
        // 핵심 회귀 방지: 음수 remaining이 X-RateLimit-Remaining 헤더로 노출되면 안 됨
        assertThatThrownBy(() -> RateLimitDecision.allowed(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remaining");
    }

    @Test
    void denied_with_positive_retry_after_is_constructed() {
        RateLimitDecision decision = RateLimitDecision.denied(Duration.ofSeconds(30));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void denied_with_zero_retry_after_is_constructed() {
        // ZERO는 음수가 아니므로 통과 (즉시 재시도 가능 시그널 — Lua TTL=-2 race 처리 경로)
        RateLimitDecision decision = RateLimitDecision.denied(Duration.ZERO);

        assertThat(decision.retryAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void denied_with_null_retry_after_throws() {
        assertThatThrownBy(() -> RateLimitDecision.denied(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryAfter");
    }

    @Test
    void denied_with_negative_retry_after_throws() {
        assertThatThrownBy(() -> RateLimitDecision.denied(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryAfter");
    }

    @Test
    void direct_constructor_validates_remaining() {
        assertThatThrownBy(() -> new RateLimitDecision(true, -5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void direct_constructor_validates_retry_after_null() {
        assertThatThrownBy(() -> new RateLimitDecision(false, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
