package com.chunbaetour.domain.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.festival.dto.response.CalendarEventItem;
import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheList;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

/**
 * KAN-264 회귀 테스트 — Redis 캐시 직렬화기가 타입정보(@class)를 보존해 구체 타입으로 역직렬화되는지 검증.
 *
 * <p>버그: default typing 미적용 직렬화기는 캐시 HIT 시 DTO를 {@link java.util.LinkedHashMap}으로 복원해
 * {@code ClassCastException}을 일으켰다(축제 목록/상세/캘린더 전면 500). 루트가 배열인 캐시는 래퍼 객체로
 * 감싸 루트를 객체로 만든다.
 */
class RedisJsonSerializerFactoryTest {

    private final GenericJacksonJsonRedisSerializer serializer =
            RedisJsonSerializerFactory.typePreservingSerializer();

    private FestivalCacheData sample() {
        return new FestivalCacheData(9204L, "천안축제", "설명", "충청남도", "천안시 주소",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10),
                null, null, FestivalStatus.ACTIVE);
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(Object value) {
        byte[] bytes = serializer.serialize(value);
        return (T) serializer.deserialize(bytes);
    }

    @Test
    @DisplayName("단일 DTO가 LinkedHashMap이 아니라 FestivalCacheData로 역직렬화된다 (상세/캐시 경로)")
    void singleDtoRoundTripsToConcreteType() {
        Object restored = roundTrip(sample());

        assertThat(restored).isInstanceOf(FestivalCacheData.class);
        FestivalCacheData result = (FestivalCacheData) restored;
        assertThat(result.id()).isEqualTo(9204L);
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.status()).isEqualTo(FestivalStatus.ACTIVE);
    }

    @Test
    @DisplayName("FestivalCacheList 래퍼가 원소까지 FestivalCacheData로 복원된다 (목록/일별 캘린더 경로)")
    void cacheListWrapperRoundTrips() {
        FestivalCacheList wrapper = FestivalCacheList.of(List.of(sample()));

        FestivalCacheList restored = roundTrip(wrapper);

        assertThat(restored).isInstanceOf(FestivalCacheList.class);
        assertThat(restored.festivals()).hasSize(1);
        assertThat(restored.festivals().get(0)).isInstanceOf(FestivalCacheData.class);
        assertThat(restored.festivals().get(0).id()).isEqualTo(9204L);
    }

    @Test
    @DisplayName("CalendarResponse(Map 포함 객체)가 구체 타입으로 복원된다 (월별 캘린더 경로)")
    void calendarResponseRoundTrips() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        CalendarResponse response = new CalendarResponse(2026, 6,
                List.of(day),
                Map.of(day, List.of(new CalendarEventItem(9204L, "천안축제", "주소", "FESTIVAL"))));

        CalendarResponse restored = roundTrip(response);

        assertThat(restored).isInstanceOf(CalendarResponse.class);
        assertThat(restored.year()).isEqualTo(2026);
        assertThat(restored.markedDates()).containsExactly(day);
        assertThat(restored.events().get(day).get(0)).isInstanceOf(CalendarEventItem.class);
        assertThat(restored.events().get(day).get(0).festivalId()).isEqualTo(9204L);
    }
}
