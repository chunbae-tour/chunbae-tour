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

    @Test
    @DisplayName("인기 추천 - Redis 캐시에 값이 있을 때 (Cache Hit, 정렬 유지 확인)")
    void getPopularRecommendations_CacheHit() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        // ID 2, 1 순서로 캐시되어 있다고 가정 (정렬 테스트)
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("2", "1", "invalid_id"))); // invalid_id는 무시되어야 함

        Place mockPlace1 = mock(Place.class);
        lenient().when(mockPlace1.getId()).thenReturn(1L);
        Place mockPlace2 = mock(Place.class);
        lenient().when(mockPlace2.getId()).thenReturn(2L);
        
        when(placeRepository.findAllById(any())).thenReturn(List.of(mockPlace1, mockPlace2));

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).placeId()).isEqualTo(2L); // 순서 보장
        assertThat(result.get(1).placeId()).isEqualTo(1L);
        verify(placeRepository, never()).findTopPopularPlaces(any());
    }

    @Test
    @DisplayName("인기 추천 - Redis 캐시 미스 시 DB Fallback 및 Cache-Aside 수행")
    void getPopularRecommendations_CacheMiss() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(Collections.emptySet());

        Place mockPlace = mock(Place.class);
        when(mockPlace.getId()).thenReturn(2L);
        when(mockPlace.getName()).thenReturn("DB 추천 명소");
        when(mockPlace.getLikeCount()).thenReturn(10);
        when(mockPlace.getViewCount()).thenReturn(20);

        when(placeRepository.findTopPopularPlaces(any())).thenReturn(List.of(mockPlace));

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
    @DisplayName("인기 추천 - Redis 조회 실패 시 DB Fallback 정상 수행")
    void getPopularRecommendations_RedisFailureFallback() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis down"));
        when(placeRepository.findTopPopularPlaces(any())).thenReturn(Collections.emptyList());

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).isEmpty();
        verify(placeRepository).findTopPopularPlaces(any());
    }

    @Test
    @DisplayName("근처 추천 - DB 조회 후 Java 메모리 셔플 및 제한 확인")
    void getNearbyRecommendations() {
        // given
        Place mock1 = mock(Place.class);
        Place mock2 = mock(Place.class);
        lenient().when(mock1.getId()).thenReturn(1L);
        lenient().when(mock2.getId()).thenReturn(2L);

        when(placeRepository.findNearbyPlacesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(new java.util.ArrayList<>(List.of(mock1, mock2)));

        // when
        List<RecommendPlaceResponse> result = recommendService.getNearbyRecommendations(37.5, 127.0, 5.0, 1);

        // then
        assertThat(result).hasSize(1); // limit 적용 확인
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
}
