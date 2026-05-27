package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.response.RecommendPlaceResponse;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RecommendService recommendService;

    private Place createTestPlace(Long id, String name, double lat, double lng) {
        Place place = Place.builder()
                .name(name)
                .category(PlaceCategory.TOURIST_SPOT)
                .address("Test Address")
                .lat(java.math.BigDecimal.valueOf(lat))
                .lng(java.math.BigDecimal.valueOf(lng))
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    @Test
    @DisplayName("인기 추천 - Redis 캐시에 값이 있을 때 (Cache Hit, 정렬 유지 확인)")
    void getPopularRecommendations_CacheHit() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        // ID 2, 1 순서로 캐시되어 있다고 가정 (정렬 테스트)
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("2", "1")));

        Place place1 = createTestPlace(1L, "Place 1", 37.5, 127.0);
        Place place2 = createTestPlace(2L, "Place 2", 37.5, 127.0);
        
        when(placeRepository.findAllById(any())).thenReturn(List.of(place1, place2));

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).placeId()).isEqualTo(2L); // 순서 보장
        assertThat(result.get(1).placeId()).isEqualTo(1L);
        verify(placeRepository, never()).findTopPopularPlaces(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("인기 추천 - Redis 캐시 미스 시 DB Fallback 및 Cache-Aside 수행")
    void getPopularRecommendations_CacheMiss() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(Collections.emptySet());

        Place place = createTestPlace(2L, "DB 추천 명소", 37.5, 127.0);
        org.springframework.test.util.ReflectionTestUtils.setField(place, "likeCount", 10);
        org.springframework.test.util.ReflectionTestUtils.setField(place, "viewCount", 20);

        when(placeRepository.findTopPopularPlaces(anyDouble(), anyDouble(), any())).thenReturn(List.of(place));

        when(stringRedisTemplate.executePipelined(any(org.springframework.data.redis.core.SessionCallback.class)))
                .thenAnswer(invocation -> {
                    org.springframework.data.redis.core.SessionCallback<?> callback = invocation.getArgument(0);
                    callback.execute(stringRedisTemplate);
                    return Collections.emptyList();
                });

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(2L);
        // ZADD(Bulk) 및 만료시간 세팅 호출 정밀 검증
        verify(zSetOperations).add(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), argThat(set -> {
            if (set == null || set.size() != 1) return false;
            ZSetOperations.TypedTuple<String> tuple = (ZSetOperations.TypedTuple<String>) set.iterator().next();
            return tuple.getValue().equals("2") && tuple.getScore() == (10 * 0.7 + 20 * 0.3);
        }));
        verify(stringRedisTemplate).expire(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), eq(PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES), eq(TimeUnit.MINUTES));
    }
    
    @Test
    @DisplayName("인기 추천 - Redis 캐시 중 유효하지 않은 ID 파싱 실패 시 해당 항목 무시")
    void getPopularRecommendations_InvalidIdIgnored() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("1", "invalid_id", "2")));

        Place place1 = createTestPlace(1L, "Place 1", 37.5, 127.0);
        Place place2 = createTestPlace(2L, "Place 2", 37.5, 127.0);
        
        when(placeRepository.findAllById(any())).thenReturn(List.of(place1, place2));

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).placeId()).isEqualTo(1L);
        assertThat(result.get(1).placeId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("인기 추천 - Redis 조회 실패 시 DB Fallback 정상 수행")
    void getPopularRecommendations_RedisFailureFallback() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis down"));
        when(placeRepository.findTopPopularPlaces(anyDouble(), anyDouble(), any())).thenReturn(Collections.emptyList());

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).isEmpty();
        verify(placeRepository).findTopPopularPlaces(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("근처 추천 - DB 조회 후 Java 메모리 셔플 및 제한 확인")
    void getNearbyRecommendations() {
        // given
        Place place1 = createTestPlace(1L, "Place 1", 37.501, 127.001);
        Place place2 = createTestPlace(2L, "Place 2", 37.502, 127.002);

        when(placeRepository.findNearbyPlacesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(new java.util.ArrayList<>(List.of(place1, place2)));

        // when
        List<RecommendPlaceResponse> result = recommendService.getNearbyRecommendations(37.5, 127.0, 5.0, 1);

        // then
        assertThat(result).hasSize(1); // limit 적용 확인
        assertThat(result.get(0).distanceMeters()).isNotNull(); // 거리 계산 확인
    }

    @Test
    @DisplayName("카테고리 추천 - 정상 조회")
    void getCategoryRecommendations() {
        // given
        when(placeRepository.findTopByCategory(eq(PlaceCategory.TOURIST_SPOT), any())).thenReturn(Collections.emptyList());

        // when
        List<RecommendPlaceResponse> result = recommendService.getCategoryRecommendations(PlaceCategory.TOURIST_SPOT);

        // then
        assertThat(result).isEmpty();
        verify(placeRepository).findTopByCategory(eq(PlaceCategory.TOURIST_SPOT), any());
    }
    @Test
    @DisplayName("카테고리 추천 - 카테고리가 null일 경우 예외 발생")
    void getCategoryRecommendations_NullCategory() {
        // given & when & then
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            recommendService.getCategoryRecommendations(null);
        });
    }
}
