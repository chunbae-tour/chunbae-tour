package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DailyChargeLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // 2026-06-12T01:00:00Z → KST 2026-06-12 10:00 → 일자 2026-06-12
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-12T01:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private DailyChargeLimiter dailyChargeLimiter;

    private static final Long USER_ID = 1L;
    private static final String KEY = "daily:charge:1:2026-06-12";

    @Test
    @DisplayName("누적액 + 요청액이 한도 이하면 통과한다")
    void assertWithinDailyLimit_underLimit_passes() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn("400000");

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 50_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("누적액 + 요청액이 한도와 정확히 같으면 통과한다 (경계값)")
    void assertWithinDailyLimit_exactlyLimit_passes() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn("400000");

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("누적액 + 요청액이 한도를 초과하면 PAY_030을 던진다")
    void assertWithinDailyLimit_overLimit_throws() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn("450000");

        assertThatThrownBy(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("오늘 누적 키가 없으면 0으로 보고 요청액만으로 판단한다")
    void assertWithinDailyLimit_noKey_treatsAsZero() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn(null);

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 장애 시 가용성 우선으로 한도 검증을 건너뛰고 통과한다")
    void assertWithinDailyLimit_redisFailure_passes() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willThrow(new RuntimeException("redis down"));

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("누적 가산은 INCRBY 후 TTL 48시간을 설정한다")
    void accumulate_incrementsAndSetsTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(KEY, 30_000L)).willReturn(30_000L);

        dailyChargeLimiter.accumulate(USER_ID, 30_000L);

        verify(valueOperations).increment(KEY, 30_000L);
        verify(redisTemplate).expire(KEY, Duration.ofHours(48));
    }

    @Test
    @DisplayName("누적 가산 중 Redis 장애가 나도 예외를 전파하지 않는다")
    void accumulate_redisFailure_swallowed() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(KEY, 30_000L)).willThrow(new RuntimeException("redis down"));

        assertThatCode(() -> dailyChargeLimiter.accumulate(USER_ID, 30_000L))
                .doesNotThrowAnyException();
    }
}
