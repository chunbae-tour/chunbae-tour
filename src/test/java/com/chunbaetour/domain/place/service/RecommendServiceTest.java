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
    @DisplayName("인기 추천 - Redis 캐시에 값이 있을 때 (Cache Hit)")
    void getPopularRecommendations_CacheHit() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(PlaceRedisConstants.RECOMMEND_POPULAR_KEY, 0, 9))
                .thenReturn(Set.of("1"));

        Place mockPlace = mock(Place.class);
        when(mockPlace.getId()).thenReturn(1L);
        when(mockPlace.getName()).thenReturn("제주도 맛집");
        when(placeRepository.findAllById(any())).thenReturn(List.of(mockPlace));

        // when
        List<RecommendPlaceResponse> result = recommendService.getPopularRecommendations();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(1L);
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
        // ZADD(Bulk) 및 만료시간 세팅 호출 검증
        verify(zSetOperations).add(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), any(Set.class));
        verify(stringRedisTemplate).expire(eq(PlaceRedisConstants.RECOMMEND_POPULAR_KEY), anyLong(), any());
    }
}
