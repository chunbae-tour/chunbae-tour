package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyChargeLimiterTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    // 2026-06-11T16:00:00Z = KST 2026-06-12 01:00 → 한도 일자는 KST 기준 2026-06-12.
    // UTC 일자(06-11)와 KST 일자(06-12)가 갈라지는 시각이라, 구현이 clock.withZone(KST)를 빼고
    // UTC 일자로 회귀하면 아래 경계 단언이 깨진다(KST 변환 회귀 가드).
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-11T16:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private DailyChargeLimiter dailyChargeLimiter;

    private static final Long USER_ID = 1L;
    // KST 2026-06-12 00:00 = UTC 2026-06-11 15:00 / KST 2026-06-13 00:00 = UTC 2026-06-12 15:00
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 6, 11, 15, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 6, 12, 15, 0);

    @Test
    @DisplayName("당일 누적 + 요청액이 한도 이하면 통과한다")
    void assertWithinDailyLimit_underLimit_passes() {
        given(paymentOrderRepository.sumChargedAmountForDailyLimit(USER_ID, START_AT, END_AT))
                .willReturn(400_000L);

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 50_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("당일 누적 + 요청액이 한도와 정확히 같으면 통과한다 (경계값)")
    void assertWithinDailyLimit_exactlyLimit_passes() {
        given(paymentOrderRepository.sumChargedAmountForDailyLimit(USER_ID, START_AT, END_AT))
                .willReturn(400_000L);

        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("당일 누적 + 요청액이 한도를 초과하면 PAY_030을 던진다")
    void assertWithinDailyLimit_overLimit_throws() {
        given(paymentOrderRepository.sumChargedAmountForDailyLimit(USER_ID, START_AT, END_AT))
                .willReturn(450_000L);

        assertThatThrownBy(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("한도 일자는 KST 자정 경계로 산정해 UTC 범위로 변환한다 (KST 변환 핀)")
    void assertWithinDailyLimit_usesKstDayBoundsInUtc() {
        given(paymentOrderRepository.sumChargedAmountForDailyLimit(eq(USER_ID), eq(START_AT), eq(END_AT)))
                .willReturn(0L);

        dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 10_000L);

        // KST 2026-06-12 영업일 → UTC [06-11 15:00, 06-12 15:00) 로 조회되는지 고정
        verify(paymentOrderRepository).sumChargedAmountForDailyLimit(USER_ID, START_AT, END_AT);
    }
}
