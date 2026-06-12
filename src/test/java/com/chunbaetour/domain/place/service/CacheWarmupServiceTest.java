package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.response.PlaceCacheDto;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CacheWarmupServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PlaceDetailEnrichmentService placeDetailEnrichmentService;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheWarmupService cacheWarmupService;

    private static final Long PLACE_ID = 1L;

    private Place createActivePlace(Long id) {
        Place place = Place.builder()
                .name("경복궁")
                .category(PlaceCategory.TOURIST_SPOT)
                .address("서울 종로구")
                .lat(BigDecimal.valueOf(37.579617))
                .lng(BigDecimal.valueOf(126.977041))
                .build();
        ReflectionTestUtils.setField(place, "id", id);
        ReflectionTestUtils.setField(place, "status", PlaceStatus.ACTIVE);
        ReflectionTestUtils.setField(place, "likeCount", 100);
        ReflectionTestUtils.setField(place, "viewCount", 500);
        return place;
    }

    // ── warmupPopularZSet ────────────────────────────────────────────────────────

    @Test
    @DisplayName("인기 ZSet 웜업 — 캐시 없음: DB 조회 후 ZSet 적재")
    void warmupPopularZSet_cacheMiss_loadsFromDb() {
        // given
        Place place = createActivePlace(PLACE_ID);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(0L);

        // when
        cacheWarmupService.warmupPopularZSet(List.of(place));

        // then
        verify(zSetOperations).add(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), any());
        verify(stringRedisTemplate).expire(
                eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY),
                eq(PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    @DisplayName("인기 ZSet 웜업 — 캐시 이미 존재: DB 조회 생략")
    void warmupPopularZSet_cacheExists_skipsDb() {
        // given — ZSet에 이미 데이터 있음
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(10L);

        // when
        cacheWarmupService.warmupPopularZSet(List.of(createActivePlace(PLACE_ID)));

        // then — DB 조회나 적재 없어야 함
        verify(zSetOperations, never()).add(anyString(), any());
    }

    @Test
    @DisplayName("인기 ZSet 웜업 — 파라미터가 비었을 때: ZSet 적재 스킵")
    void warmupPopularZSet_emptyList_skipsCache() {
        // given
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(0L);

        // when
        cacheWarmupService.warmupPopularZSet(List.of());

        // then
        verify(zSetOperations, never()).add(anyString(), any());
    }

    @Test
    @DisplayName("인기 ZSet 웜업 — Redis 오류 발생 시 예외를 삼키고 정상 반환")
    void warmupPopularZSet_redisError_doesNotThrow() {
        // given
        given(stringRedisTemplate.opsForZSet()).willThrow(new RuntimeException("Redis 연결 오류"));

        // when & then — 예외 전파 없이 정상 반환
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> cacheWarmupService.warmupPopularZSet(List.of(createActivePlace(PLACE_ID)))
        );
    }

    // ── warmupPlaceDetails ───────────────────────────────────────────────────────

    @Test
    @DisplayName("관광지 상세 웜업 — 캐시 없음: JSON 직렬화 후 Redis SET")
    void warmupPlaceDetails_cacheMiss_storesInRedis() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(place)).willReturn(place);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // likeCount Redis miss
        given(objectMapper.writeValueAsString(any())).willReturn("{\"placeId\":1}");

        // when
        cacheWarmupService.warmupPlaceDetails(List.of(place));

        // then
        verify(valueOperations).set(eq(cacheKey), anyString(), any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — 캐시 이미 존재: 직렬화 및 SET 스킵")
    void warmupPlaceDetails_cacheExists_skipsWrite() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(true);

        // when
        cacheWarmupService.warmupPlaceDetails(List.of(place));

        // then — Redis SET이 호출되지 않아야 함
        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — enrichment 미완료 관광지 스킵")
    void warmupPlaceDetails_enrichmentPending_skipsWrite() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);
        ReflectionTestUtils.setField(place, "enrichAttemptCount", 0); // 실제 필드명 사용
        ReflectionTestUtils.setField(place, "source", com.chunbaetour.domain.place.type.PlaceSource.API_FETCH);
        ReflectionTestUtils.setField(place, "externalId", "12345");
        // description은 null이므로 needsDetailEnrichment = true

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(place)).willReturn(place);

        // when
        cacheWarmupService.warmupPlaceDetails(List.of(place));

        // then — 직렬화 없이 스킵
        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — 파라미터가 비었을 때: 조기 반환")
    void warmupPlaceDetails_emptyList_returnsEarly() throws Exception {
        // when
        cacheWarmupService.warmupPlaceDetails(List.of());

        // then — hasKey, enrichIfNeeded 등 이후 로직 미호출
        verify(stringRedisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — 개별 직렬화 실패 시 다음 항목 계속 진행")
    void warmupPlaceDetails_serializationError_continuesNextPlace() throws Exception {
        Place place1 = createActivePlace(1L);
        ReflectionTestUtils.setField(place1, "description", "설명"); // needsDetailEnrichment = false 유도
        Place place2 = createActivePlace(2L);
        ReflectionTestUtils.setField(place2, "description", "설명");

        given(stringRedisTemplate.hasKey(anyString())).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(any())).willAnswer(inv -> inv.getArgument(0));
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        
        // 첫 번째 직렬화 실패, 두 번째 성공 모킹
        given(objectMapper.writeValueAsString(any()))
                .willThrow(new RuntimeException("직렬화 오류"))
                .willReturn("{\"placeId\":2}");

        // when & then — 예외 전파 안됨 검증
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> cacheWarmupService.warmupPlaceDetails(List.of(place1, place2))
        );
        
        // 두 번째 place는 직렬화에 성공하여 SET이 1번 호출되었는지 검증
        verify(valueOperations, org.mockito.Mockito.times(1)).set(anyString(), anyString(), any());
    }
}
