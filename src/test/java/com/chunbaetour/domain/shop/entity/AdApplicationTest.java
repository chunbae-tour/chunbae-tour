package com.chunbaetour.domain.shop.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.AdType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdApplicationTest {

    private static final Long SHOP_ID = 10L;
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 30);

    private AdApplication pendingAd() {
        return AdApplication.create(SHOP_ID, AdType.BANNER, START, END, 30_000L);
    }

    private AdApplication approvedAd() {
        AdApplication ad = pendingAd();
        ad.approve();
        return ad;
    }

    @Nested
    @DisplayName("approve()")
    class Approve {

        @Test
        @DisplayName("PENDING → APPROVED 전이 성공")
        void approve_pendingToApproved() {
            AdApplication ad = pendingAd();
            ad.approve();
            assertThat(ad.getStatus()).isEqualTo(com.chunbaetour.domain.shop.type.AdApplicationStatus.APPROVED);
        }

        @Test
        @DisplayName("PENDING 아닌 상태에서 approve() → AD_APPLICATION_INVALID_STATUS")
        void approve_notPending_throws() {
            AdApplication ad = pendingAd();
            ad.approve();
            assertThatThrownBy(ad::approve)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
    }

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("PENDING → REJECTED 전이 성공")
        void reject_pendingToRejected() {
            AdApplication ad = pendingAd();
            ad.reject("부적절한 광고 내용");
            assertThat(ad.getStatus()).isEqualTo(com.chunbaetour.domain.shop.type.AdApplicationStatus.REJECTED);
        }

        @Test
        @DisplayName("PENDING 아닌 상태에서 reject() → AD_APPLICATION_INVALID_STATUS")
        void reject_notPending_throws() {
            AdApplication ad = pendingAd();
            ad.approve();
            assertThatThrownBy(() -> ad.reject("이유"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
    }

    @Nested
    @DisplayName("extend()")
    class Extend {

        @Test
        @DisplayName("APPROVED 광고 연장 성공 — endDate 증가")
        void extend_approved_extendEndDate() {
            AdApplication ad = approvedAd();
            ad.extend(5, START);
            assertThat(ad.getEndDate()).isEqualTo(END.plusDays(5));
        }

        @Test
        @DisplayName("APPROVED 아닌 상태에서 extend() → AD_APPLICATION_INVALID_STATUS")
        void extend_notApproved_throws() {
            AdApplication ad = pendingAd();
            assertThatThrownBy(() -> ad.extend(5, START))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }

        @Test
        @DisplayName("이미 만료된 광고 연장 → AD_APPLICATION_INVALID_STATUS")
        void extend_expiredAd_throws() {
            AdApplication ad = approvedAd();
            LocalDate tomorrow = END.plusDays(1);
            assertThatThrownBy(() -> ad.extend(5, tomorrow))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }

        @Test
        @DisplayName("마지막 날(endDate 당일) 연장 — 만료 전이므로 허용")
        void extend_onEndDate_allowed() {
            AdApplication ad = approvedAd();
            ad.extend(3, END);
            assertThat(ad.getEndDate()).isEqualTo(END.plusDays(3));
        }
    }

    @Nested
    @DisplayName("calculateExtensionCost()")
    class CalculateExtensionCost {

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

    } // CalculateExtensionCost
}
