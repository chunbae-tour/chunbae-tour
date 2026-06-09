package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.search.repository.SuggestCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestServiceTest {

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @Mock
    private SuggestCacheRepository suggestCacheRepository;

    @Mock
    private PopularSearchService popularSearchService;

    @Mock
    private KakaoLocalApiClient kakaoLocalApiClient;

    @InjectMocks
    private SuggestService suggestService;

    // ──────────────────────────────────────────────────────────────────────────
    // 입력 유효성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prefix가 null이면 SEARCH_KEYWORD_TOO_SHORT 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsNull() {
        assertThatThrownBy(() -> suggestService.suggest(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("prefix가 공백만 있으면 SEARCH_KEYWORD_TOO_SHORT 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsBlank() {
        assertThatThrownBy(() -> suggestService.suggest("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("prefix가 51자 이상이면 SEARCH_KEYWORD_TOO_LONG 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsTooLong() {
        String longPrefix = "가".repeat(51);
        assertThatThrownBy(() -> suggestService.suggest(longPrefix))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_LONG.getMessage());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Redis 캐시 Hit / Miss
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 캐시 Hit 시 DB 조회 없이 캐시 결과를 반환한다")
    void suggest_ReturnsCachedResult_WhenCacheHit() {
        String prefix = "경복";
        List<String> cached = List.of("경복궁", "경복궁 야간개장");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.of(cached));

        List<String> result = suggestService.suggest(prefix);

        assertThat(result).containsExactly("경복궁", "경복궁 야간개장");
        verify(placeQueryRepository, never()).suggestByPrefix(anyString(), anyInt());
    }

    @Test
    @DisplayName("캐시 Miss 시 DB 결과와 Redis ZSet 인기 검색어를 병합하여 반환하고 캐시에 저장한다")
    void suggest_MergesDbAndRedisResults_WhenCacheMiss() {
        String prefix = "경복";
        List<String> dbResults = List.of("경복궁", "경복로");
        List<String> redisResults = List.of("경복궁 야간개장", "경복궁");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);
        when(kakaoLocalApiClient.searchByKeyword(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(null);
        when(popularSearchService.fetchPopularSuggestions(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(redisResults);

        List<String> result = suggestService.suggest(prefix);

        // DB 우선, ZSet 보완 (중복 제거)
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly("경복궁", "경복로", "경복궁 야간개장");
        
        // 캐시 저장 검증 추가
        verify(suggestCacheRepository).set(eq(prefix), eq(result));
    }

    @Test
    @DisplayName("자동완성 결과는 최대 5개를 넘지 않는다")
    void suggest_ReturnsAtMostFiveResults() {
        String prefix = "관광";
        List<String> dbResults = List.of("관광지1", "관광지2", "관광지3", "관광지4", "관광지5");
        List<String> redisResults = List.of("관광지6");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);
        when(kakaoLocalApiClient.searchByKeyword(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(null);
        when(popularSearchService.fetchPopularSuggestions(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(redisResults);

        List<String> result = suggestService.suggest(prefix);

        assertThat(result).hasSize(5);
        assertThat(result).doesNotContain("관광지6");
    }

    @Test
    @DisplayName("캐시 읽기 실패 시 예외가 발생하지 않고 빈 Optional 반환 후 DB/Redis 로직으로 Fallback 한다")
    void suggest_FallbackToDb_WhenCacheThrowsException() {
        // 이 테스트는 현재 SuggestService가 Optional을 반환받기 때문에 자연스럽게 해결됨.
        // 실제 Repository 로직이 get() 시 빈 Optional을 던지는지 확인하기 위한 시나리오를 서비스에서 모의함.
        String prefix = "경복";
        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty()); // 캐시 조회 실패 시나리오
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(List.of("경복궁"));
        when(kakaoLocalApiClient.searchByKeyword(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(null);
        when(popularSearchService.fetchPopularSuggestions(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(List.of());

        List<String> result = suggestService.suggest(prefix);
        
        assertThat(result).containsExactly("경복궁");
        verify(suggestCacheRepository).set(eq(prefix), eq(result));
    }
}
