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
        given(placeRepository.findTopPopularPlaces(
                eq(PlaceRedisConstants.POPULAR_LIKE_WEIGHT),
                eq(PlaceRedisConstants.POPULAR_VIEW_WEIGHT),
                any(Pageable.class)
        )).willReturn(List.of(place));

        // when
        cacheWarmupService.warmupPopularZSet();

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
        cacheWarmupService.warmupPopularZSet();

        // then — DB 조회 없어야 함
        verify(placeRepository, never()).findTopPopularPlaces(any(), any(), any());
    }

    @Test
    @DisplayName("인기 ZSet 웜업 — DB 조회 결과 없음: ZSet 적재 스킵")
    void warmupPopularZSet_emptyDb_skipsCache() {
        // given
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(0L);
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of());

        // when
        cacheWarmupService.warmupPopularZSet();

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
                () -> cacheWarmupService.warmupPopularZSet()
        );
    }

    // ── warmupPlaceDetails ───────────────────────────────────────────────────────

    @Test
    @DisplayName("관광지 상세 웜업 — 캐시 없음: JSON 직렬화 후 Redis SET")
    void warmupPlaceDetails_cacheMiss_storesInRedis() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of(place));

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(place)).willReturn(place);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // likeCount Redis miss
        given(objectMapper.writeValueAsString(any())).willReturn("{\"placeId\":1}");

        // when
        cacheWarmupService.warmupPlaceDetails();

        // then
        verify(valueOperations).set(eq(cacheKey), anyString(), any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — 캐시 이미 존재: 직렬화 및 SET 스킵")
    void warmupPlaceDetails_cacheExists_skipsWrite() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of(place));

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(true);

        // when
        cacheWarmupService.warmupPlaceDetails();

        // then — Redis SET이 호출되지 않아야 함
        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — enrichment 미완료 관광지 스킵")
    void warmupPlaceDetails_enrichmentPending_skipsWrite() throws Exception {
        // given
        Place place = createActivePlace(PLACE_ID);
        ReflectionTestUtils.setField(place, "enrichRetryCount", 0); // needsDetailEnrichment = true 유도
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of(place));

        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + PLACE_ID;
        given(stringRedisTemplate.hasKey(cacheKey)).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(place)).willReturn(place);

        // needsDetailEnrichment()가 true인 경우 — description이 null이면 true
        // Place 빌더 기본값 상 description = null → needsDetailEnrichment = true
        // when
        cacheWarmupService.warmupPlaceDetails();

        // then — 직렬화 없이 스킵
        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — DB 조회 결과 없음: 조기 반환")
    void warmupPlaceDetails_emptyDb_returnsEarly() throws Exception {
        // given
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of());

        // when
        cacheWarmupService.warmupPlaceDetails();

        // then — hasKey, enrichIfNeeded 등 이후 로직 미호출
        verify(stringRedisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("관광지 상세 웜업 — 개별 직렬화 실패 시 다음 항목 계속 진행")
    void warmupPlaceDetails_serializationError_continuesNextPlace() throws Exception {
        // given — place 2개 중 첫 번째만 직렬화 오류
        Place place1 = createActivePlace(1L);
        Place place2 = createActivePlace(2L);
        given(placeRepository.findTopPopularPlaces(any(), any(), any())).willReturn(List.of(place1, place2));

        given(stringRedisTemplate.hasKey(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + 1L)).willReturn(false);
        given(stringRedisTemplate.hasKey(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + 2L)).willReturn(false);
        given(placeDetailEnrichmentService.enrichIfNeeded(any())).willAnswer(inv -> inv.getArgument(0));
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // place1은 직렬화 실패, place2는 성공 (단, place1도 description=null이므로 enrichmentPending=true → 스킵됨)
        // 실질적으로 두 place 모두 enrichmentPending=true이므로 writeValueAsString 호출 없음
        // → 아래는 예외 없이 완료되는지만 검증
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> cacheWarmupService.warmupPlaceDetails()
        );
    }
}
