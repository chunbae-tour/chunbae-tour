package com.chunbaetour.domain.shop.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.AdType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdApplicationTest {

    private static final Long SHOP_ID = 10L;

    @Test
    @DisplayName("광고 연장 비용 계산 — 30일 광고를 1일 연장하면 일 단가만큼 청구")
    void calculateExtensionCost_thirtyDayAd_oneDay() {
        AdApplication application = AdApplication.create(
                SHOP_ID,
                AdType.BANNER,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                30_000L
        );

        assertThat(application.calculateExtensionCost(1)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("광고 연장 비용 계산 — 정수 나눗셈 결과가 0이면 올림 처리")
    void calculateExtensionCost_roundsUp() {
        AdApplication application = AdApplication.create(
                SHOP_ID,
                AdType.BANNER,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                1L
        );

        assertThat(application.calculateExtensionCost(1)).isEqualTo(1L);
    }

    @Test
    @DisplayName("광고 연장 비용 계산 — 저장 데이터의 날짜 범위가 역전되면 예외")
    void calculateExtensionCost_invalidStoredDateRange() {
        AdApplication application = AdApplication.create(
                SHOP_ID,
                AdType.BANNER,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                30_000L
        );
        ReflectionTestUtils.setField(application, "endDate", LocalDate.of(2026, 5, 31));

        assertThatThrownBy(() -> application.calculateExtensionCost(1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
    }
}
