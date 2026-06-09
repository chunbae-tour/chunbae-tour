package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoKeywordResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.search.dto.response.SuggestResponse;
import com.chunbaetour.domain.search.repository.SuggestCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @org.mockito.Spy
    private java.util.concurrent.ExecutorService kakaoVirtualThreadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    @InjectMocks
    private SuggestService suggestService;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (kakaoVirtualThreadExecutor != null && !kakaoVirtualThreadExecutor.isShutdown()) {
            kakaoVirtualThreadExecutor.shutdownNow();
        }
    }

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
    // 캐시 Hit / Miss 및 병합 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 캐시 Hit 시 외부 호출 없이 캐시 결과를 반환한다")
    void suggest_ReturnsCachedResult_WhenCacheHit() {
        String prefix = "경복";
        List<SuggestResponse> cached = List.of(
                new SuggestResponse("경복궁", SuggestResponse.SuggestSource.DB),
                new SuggestResponse("경복궁 야간개장", SuggestResponse.SuggestSource.REDIS)
        );

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.of(cached));

        List<SuggestResponse> result = suggestService.suggest(prefix);

        assertThat(result).hasSize(2)
                .extracting(SuggestResponse::keyword)
                .containsExactly("경복궁", "경복궁 야간개장");
        verify(placeQueryRepository, never()).suggestByPrefix(anyString(), anyInt());
        verify(kakaoLocalApiClient, never()).searchByKeyword(anyString(), anyInt());
    }

    @Test
    @DisplayName("캐시 Miss 시 DB, Kakao, Redis를 병합하며 중복을 제거한다")
    void suggest_MergesSources_WhenCacheMiss() {
        String prefix = "경복";
        
        List<String> dbResults = List.of("경복궁", "경복로");
        KakaoKeywordResponse kakaoResponse = new KakaoKeywordResponse(
                List.of(
                        new KakaoKeywordResponse.Document(null, "경복궁", null, null, null, null, null, null, null, null), 
                        new KakaoKeywordResponse.Document(null, "경복궁역", null, null, null, null, null, null, null, null)
                ),
                null
        );
        List<String> redisResults = List.of("경복궁역", "경복궁 야간개장");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);
        when(kakaoLocalApiClient.searchByKeyword(prefix, 3)).thenReturn(kakaoResponse); // 5 - 2(db) = 3
        when(popularSearchService.fetchPopularSuggestions(prefix, 2)).thenReturn(redisResults); // 5 - 3(db+kakao) = 2

        List<SuggestResponse> result = suggestService.suggest(prefix);

        // DB(경복궁, 경복로) -> Kakao(경복궁(중복), 경복궁역) -> Redis(경복궁역(중복), 경복궁 야간개장)
        assertThat(result).hasSize(4);
        assertThat(result).extracting(SuggestResponse::keyword)
                .containsExactly("경복궁", "경복로", "경복궁역", "경복궁 야간개장");
        
        assertThat(result.get(0).source()).isEqualTo(SuggestResponse.SuggestSource.DB); // 경복궁
        assertThat(result.get(1).source()).isEqualTo(SuggestResponse.SuggestSource.DB); // 경복로
        assertThat(result.get(2).source()).isEqualTo(SuggestResponse.SuggestSource.KAKAO); // 경복궁역
        assertThat(result.get(3).source()).isEqualTo(SuggestResponse.SuggestSource.REDIS); // 경복궁 야간개장
        
        verify(suggestCacheRepository).set(eq(prefix), eq(result));
    }

    @Test
    @DisplayName("DB 결과만으로 5개가 채워지면 Kakao와 Redis는 호출되지 않는다")
    void suggest_ShortCircuits_WhenDbIsFull() {
        String prefix = "관광";
        List<String> dbResults = List.of("관광1", "관광2", "관광3", "관광4", "관광5");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);

        List<SuggestResponse> result = suggestService.suggest(prefix);

        assertThat(result).hasSize(5);
        verify(kakaoLocalApiClient, never()).searchByKeyword(anyString(), anyInt());
        verify(popularSearchService, never()).fetchPopularSuggestions(anyString(), anyInt());
    }

    @Test
    @DisplayName("DB와 Kakao 결과만으로 5개가 채워지면 Redis는 호출되지 않는다")
    void suggest_ShortCircuits_WhenDbAndKakaoAreFull() {
        String prefix = "관광";
        List<String> dbResults = List.of("관광1", "관광2", "관광3");
        KakaoKeywordResponse kakaoResponse = new KakaoKeywordResponse(
                List.of(
                        new KakaoKeywordResponse.Document(null, "관광4", null, null, null, null, null, null, null, null), 
                        new KakaoKeywordResponse.Document(null, "관광5", null, null, null, null, null, null, null, null)
                ),
                null
        );

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);
        when(kakaoLocalApiClient.searchByKeyword(prefix, 2)).thenReturn(kakaoResponse); // 5 - 3 = 2

        List<SuggestResponse> result = suggestService.suggest(prefix);

        assertThat(result).hasSize(5);
        verify(popularSearchService, never()).fetchPopularSuggestions(anyString(), anyInt());
    }

    @Test
    @DisplayName("Kakao API 호출 시 예외나 타임아웃이 발생하면 무시하고 Redis 보완 조회를 진행한다")
    void suggest_ContinuesWithRedis_WhenKakaoThrowsException() {
        String prefix = "경복";
        List<String> dbResults = List.of("경복궁");
        List<String> redisResults = List.of("경복궁 야간개장");

        when(suggestCacheRepository.get(prefix)).thenReturn(Optional.empty());
        when(placeQueryRepository.suggestByPrefix(prefix, SuggestService.SUGGEST_MAX_SIZE)).thenReturn(dbResults);
        
        // Kakao API 예외 발생 모킹
        when(kakaoLocalApiClient.searchByKeyword(prefix, 4)).thenThrow(new RuntimeException("Kakao Timeout"));
        
        when(popularSearchService.fetchPopularSuggestions(prefix, 4)).thenReturn(redisResults);

        List<SuggestResponse> result = suggestService.suggest(prefix);

        // Kakao가 실패했어도 DB(1) + Redis(1) 결과가 정상 반환되어야 함
        assertThat(result).hasSize(2)
                .extracting(SuggestResponse::keyword)
                .containsExactly("경복궁", "경복궁 야간개장");
        
        verify(suggestCacheRepository).set(eq(prefix), eq(result));
    }
}
