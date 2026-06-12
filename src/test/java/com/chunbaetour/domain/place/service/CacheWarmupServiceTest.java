package com.chunbaetour.domain.place.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceSource;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CacheWarmupServiceTest {

    // onApplicationReady의 DB 조회 모킹을 위해 사용. 하위 메서드 테스트에서는 직접 파라미터를 넘기므로 사용되지 않음.
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

    @Spy
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

    // ── onApplicationReady ───────────────────────────────────────────────────────

    @Test
    @DisplayName("웜업 메인 — DB에서 조회 후 하위 메서드로 데이터 분배 (슬라이싱 확인)")
    void onApplicationReady_loadsFromDbAndDistributes() {
        // given: ZSet_TOP_N (10)보다 많은 11개의 데이터를 세팅하여 subList 검증
        List<Place> topPlaces = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> createActivePlace((long) i))
                .toList();

        given(placeRepository.findTopPopularPlaces(any(Double.class), any(Double.class), any()))
                .willReturn(topPlaces);

        // 하위 메서드의 실제 동작은 방지하고 호출 여부만 검증하기 위해 doNothing 사용
        doNothing().when(cacheWarmupService).warmupPopularZSet(any());
        doNothing().when(cacheWarmupService).warmupPlaceDetails(any());

        // when
        cacheWarmupService.onApplicationReady();

        // then: warmupPopularZSet에는 슬라이싱된 10개만, warmupPlaceDetails에는 11개 전체 전달
        verify(cacheWarmupService).warmupPopularZSet(topPlaces.subList(0, 10));
        verify(cacheWarmupService).warmupPlaceDetails(topPlaces);
    }

    @Test
    @DisplayName("웜업 메인 — DB 조회 결과 없으면 하위 메서드 스킵")
    void onApplicationReady_emptyDb_skipsWarmup() {
        // given
        given(placeRepository.findTopPopularPlaces(any(Double.class), any(Double.class), any()))
                .willReturn(List.of());

        // when
        cacheWarmupService.onApplicationReady();

        // then
        verify(cacheWarmupService, never()).warmupPopularZSet(any());
        verify(cacheWarmupService, never()).warmupPlaceDetails(any());
    }

    // ── warmupPopularZSet ────────────────────────────────────────────────────────

    @Test
    @DisplayName("인기 ZSet 웜업 — 캐시 없음: DB 조회 후 ZSet 적재")
    void warmupPopularZSet_cacheMiss_loadsFromDb() {
        // given
        Place place = createActivePlace(PLACE_ID);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(0L);
        given(stringRedisTemplate.expire(anyString(), any(Long.class), any())).willReturn(true);

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
        assertDoesNotThrow(
                () -> cacheWarmupService.warmupPopularZSet(List.of(createActivePlace(PLACE_ID)))
        );
    }

    @Test
    @DisplayName("인기 ZSet 웜업 — TTL 설정 실패 시 Partial Write 방지를 위해 롤백(삭제)")
    void warmupPopularZSet_ttlFail_rollbacks() {
        // given
        Place place = createActivePlace(PLACE_ID);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(zSetOperations.zCard(PlaceRedisConstants.RECOMMEND_POPULAR_KEY)).willReturn(0L);
        
        // expire가 false 반환(실패) 모킹
        given(stringRedisTemplate.expire(anyString(), any(Long.class), any())).willReturn(false);

        // when
        cacheWarmupService.warmupPopularZSet(List.of(place));

        // then
        verify(zSetOperations).add(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), any());
        // TTL 실패로 인해 delete가 호출되어야 함
        verify(stringRedisTemplate).delete(PlaceRedisConstants.RECOMMEND_POPULAR_KEY);
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
        ReflectionTestUtils.setField(place, "source", PlaceSource.API_FETCH);
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
        assertDoesNotThrow(
                () -> cacheWarmupService.warmupPlaceDetails(List.of(place1, place2))
        );
        
        // 두 번째 place는 직렬화에 성공하여 SET이 1번 호출되었는지 검증
        verify(valueOperations, times(1)).set(anyString(), anyString(), any());
    }
}
