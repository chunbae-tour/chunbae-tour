package com.chunbaetour.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.type.PlaceCategory;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceEnrichmentTest {

    private Place apiPlace() {
        return Place.createFromApi("123456", "관광지", "서울특별시 중구 세종대로 110",
                new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, "서울특별시", "중구");
    }

    @Test
    @DisplayName("API 수집 관광지(설명 미수집)는 상세 수집 대상이다")
    void apiPlaceNeedsEnrichment() {
        assertThat(apiPlace().needsDetailEnrichment()).isTrue();
    }

    @Test
    @DisplayName("수동 등록(MANUAL) 관광지는 상세 수집 대상이 아니다")
    void manualPlaceDoesNotNeedEnrichment() {
        Place manual = Place.builder()
                .name("수동관광지").category(PlaceCategory.TOURIST_SPOT)
                .address("서울특별시 중구 세종대로 110")
                .lat(new BigDecimal("37.5")).lng(new BigDecimal("127.0"))
                .build();

        assertThat(manual.needsDetailEnrichment()).isFalse();
    }

    @Test
    @DisplayName("overview가 채워지면(applyApiDetail) 더 이상 수집 대상이 아니다")
    void enrichedPlaceDoesNotNeedEnrichment() {
        Place place = apiPlace();

        place.applyApiDetail("설명입니다", "10:00-18:00", "월요일");

        assertThat(place.needsDetailEnrichment()).isFalse();
        assertThat(place.getDescription()).isEqualTo("설명입니다");
    }

    @Test
    @DisplayName("빈 overview 재시도 횟수가 한도(MAX_ENRICH_ATTEMPTS)에 도달하면 더 이상 수집하지 않는다")
    void exhaustedRetriesStopEnrichment() {
        Place place = apiPlace();

        for (int i = 0; i < Place.MAX_ENRICH_ATTEMPTS; i++) {
            assertThat(place.needsDetailEnrichment()).isTrue(); // 한도 미만 동안은 계속 대상
            place.recordEmptyEnrichAttempt();
        }

        // 한도 도달 — 설명은 여전히 null이지만 더 이상 외부 API를 호출하지 않는다
        assertThat(place.needsDetailEnrichment()).isFalse();
        assertThat(place.getDescription()).isNull();
    }

    @Test
    @DisplayName("한도 소진 후 외부 modifiedtime이 증가하면 재시도 한도가 리셋되어 다시 수집 대상이 된다")
    void newerModifiedTimeResetsRetries() {
        Place place = apiPlace();
        place.syncExternalModifiedTime("20260101000000"); // 최초 동기화 시각

        for (int i = 0; i < Place.MAX_ENRICH_ATTEMPTS; i++) {
            place.recordEmptyEnrichAttempt();
        }
        assertThat(place.needsDetailEnrichment()).isFalse(); // 한도 소진

        place.syncExternalModifiedTime("20260601000000"); // 외부 데이터 갱신(modifiedtime 증가)

        assertThat(place.needsDetailEnrichment()).isTrue(); // 재수집 기회 회복
    }

    @Test
    @DisplayName("외부 modifiedtime이 동일하거나 과거이면 재시도 한도를 리셋하지 않는다(API 반복 호출 방지)")
    void sameOrOlderModifiedTimeDoesNotReset() {
        Place place = apiPlace();
        place.syncExternalModifiedTime("20260601000000");
        for (int i = 0; i < Place.MAX_ENRICH_ATTEMPTS; i++) {
            place.recordEmptyEnrichAttempt();
        }
        assertThat(place.needsDetailEnrichment()).isFalse();

        place.syncExternalModifiedTime("20260601000000"); // 동일 (==boundary 재수집 상황)
        assertThat(place.needsDetailEnrichment()).isFalse();

        place.syncExternalModifiedTime("20250101000000"); // 과거
        assertThat(place.needsDetailEnrichment()).isFalse();
    }
}
