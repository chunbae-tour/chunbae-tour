package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.like.dto.CategoryCount;
import com.chunbaetour.domain.like.repository.UserLikeRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SearchPlacePersonalizationService 단위 테스트.
 * <p>
 * Redis 캐시 히트/미스, DB 조회, graceful degradation 케이스를 검증한다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SearchPlacePersonalizationServiceTest {

    @Mock
    private UserLikeRepository userLikeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SearchPlacePersonalizationService personalizationService;

    // ──────────────────────────────────────────────────────────────────────────
    // 비로그인(userId=null) 케이스
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("userId가 null이면 빈 리스트를 즉시 반환한다 (DB/Redis 조회 없음)")
    void getPreferredCategories_ReturnsEmpty_WhenUserIdIsNull() {
        // when
        List<PlaceCategory> result = personalizationService.getPreferredCategories(null);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(userLikeRepository);
        verifyNoInteractions(stringRedisTemplate);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Redis 캐시 히트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 캐시 히트 시 DB 조회 없이 캐시 값을 반환한다")
    void getPreferredCategories_ReturnsCachedValue_WhenCacheHit() {
        // given
        Long userId = 42L;
        String cachedValue = "TOURIST_SPOT,TRADITIONAL_MARKET";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:personalization:user:42:categories"))
                .thenReturn(cachedValue);

        // when
        List<PlaceCategory> result = personalizationService.getPreferredCategories(userId);

        // then: 캐시에서 읽은 값이 반환되어야 함
        assertThat(result).containsExactly(PlaceCategory.TOURIST_SPOT, PlaceCategory.TRADITIONAL_MARKET);
        // DB 조회는 없어야 함
        verifyNoInteractions(userLikeRepository);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Redis 캐시 미스 → DB 조회
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 미스 시 DB에서 조회 후 Redis에 캐싱하고 결과를 반환한다")
    void getPreferredCategories_QueriesDbAndCaches_WhenCacheMiss() {
        // given
        Long userId = 42L;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // 캐시 미스

        // DB: TOURIST_SPOT(5번), TRADITIONAL_MARKET(3번) 찜 — CategoryCount Projection mock
        CategoryCount row1 = mockCategoryCount(PlaceCategory.TOURIST_SPOT, 5L);
        CategoryCount row2 = mockCategoryCount(PlaceCategory.TRADITIONAL_MARKET, 3L);
        when(userLikeRepository.findLikedPlaceCategoryCountsByUserId(userId))
                .thenReturn(List.of(row1, row2));

        // when
        List<PlaceCategory> result = personalizationService.getPreferredCategories(userId);

        // then
        assertThat(result).containsExactly(PlaceCategory.TOURIST_SPOT, PlaceCategory.TRADITIONAL_MARKET);
        // Redis에 캐싱되었어야 함
        verify(valueOperations).set(
                eq("search:personalization:user:42:categories"),
                eq("TOURIST_SPOT,TRADITIONAL_MARKET"),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    @DisplayName("DB가 반환한 결과를 그대로 서비스가 반환한다 (LIMIT은 DB에서 처리됨)")
    void getPreferredCategories_ReturnsMaxThree_WhenManyCategories() {
        // given
        Long userId = 1L;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        CategoryCount r1 = mockCategoryCount(PlaceCategory.TOURIST_SPOT, 10L);
        CategoryCount r2 = mockCategoryCount(PlaceCategory.TRADITIONAL_MARKET, 8L);
        when(userLikeRepository.findLikedPlaceCategoryCountsByUserId(userId))
                .thenReturn(List.of(r1, r2));

        // when
        List<PlaceCategory> result = personalizationService.getPreferredCategories(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(
                PlaceCategory.TOURIST_SPOT,
                PlaceCategory.TRADITIONAL_MARKET
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 찜 이력 없는 경우
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("찜한 관광지가 없으면 빈 리스트를 반환하고 Redis에 캐싱하지 않는다")
    void getPreferredCategories_ReturnsEmpty_WhenNoLikes() {
        // given
        Long userId = 99L;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userLikeRepository.findLikedPlaceCategoryCountsByUserId(userId))
                .thenReturn(Collections.emptyList());

        // when
        List<PlaceCategory> result = personalizationService.getPreferredCategories(userId);

        // then: 빈 리스트 반환, Redis 캐싱 없음
        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Graceful Degradation (Redis 장애)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 장애 시 예외 없이 빈 리스트를 반환한다 (graceful degradation)")
    void getPreferredCategories_ReturnsEmpty_WhenRedisThrowsException() {
        // given
        Long userId = 42L;
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

        // when: 예외가 밖으로 나오지 않아야 함
        List<PlaceCategory> result = personalizationService.getPreferredCategories(userId);

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // evictCache
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evictCache는 올바른 Redis 키를 삭제한다")
    void evictCache_DeletesCorrectKey() {
        // when
        personalizationService.evictCache(42L);

        // then
        verify(stringRedisTemplate).delete("search:personalization:user:42:categories");
    }

    @Test
    @DisplayName("evictCache에서 userId가 null이면 아무 작업도 하지 않는다")
    void evictCache_DoesNothing_WhenUserIdIsNull() {
        // when
        personalizationService.evictCache(null);

        // then
        verifyNoInteractions(stringRedisTemplate);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 테스트 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * CategoryCount Projection을 모키토로 만드는 헬퍼.
     * nativeQuery는 실제 Hibernate Projection 프록시를 반환하므로, 테스트에서는 Mock으로 대체한다.
     * getCount()는 서비스 레이어에서 호출하지 않으므로 lenient로 설정하여 UnnecessaryStubbingException을 방지한다.
     */
    private CategoryCount mockCategoryCount(PlaceCategory category, Long count) {
        CategoryCount mock = org.mockito.Mockito.mock(CategoryCount.class);
        when(mock.getCategory()).thenReturn(category);
        org.mockito.Mockito.lenient().when(mock.getCount()).thenReturn(count);
        return mock;
    }
}
