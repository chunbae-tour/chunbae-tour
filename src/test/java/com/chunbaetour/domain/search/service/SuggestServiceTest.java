package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검색어 자동완성 (Phase 2-4) 단위 테스트.
 * <p>
 * {@link SearchService#suggest(String)} 메서드의 핵심 분기를 검증한다.
 * Redis 캐시 Hit/Miss, DB + ZSet 통합 로직, 중복 제거, 입력 유효성 등을 커버한다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SuggestServiceTest {

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @Mock
    private FestivalQueryRepository festivalQueryRepository;

    @Mock
    private PopularSearchService popularSearchService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Spy
    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    @InjectMocks
    private SearchService searchService;

    // ──────────────────────────────────────────────────────────────────────────
    // 입력 유효성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prefix가 null이면 SEARCH_KEYWORD_TOO_SHORT 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsNull() {
        // when & then
        assertThatThrownBy(() -> searchService.suggest(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("prefix가 공백만 있으면 SEARCH_KEYWORD_TOO_SHORT 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsBlank() {
        // when & then
        assertThatThrownBy(() -> searchService.suggest("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("prefix가 51자 이상이면 SEARCH_KEYWORD_TOO_LONG 예외를 던진다")
    void suggest_ThrowsException_WhenPrefixIsTooLong() {
        // given
        String longPrefix = "가".repeat(51);

        // when & then
        assertThatThrownBy(() -> searchService.suggest(longPrefix))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_LONG.getMessage());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Redis 캐시 Hit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 캐시 Hit 시 DB 조회 없이 캐시 결과를 반환한다")
    void suggest_ReturnsCachedResult_WhenCacheHit() {
        // given
        String prefix = "경복";
        List<String> cached = List.of("경복궁", "경복궁 야간개장");

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        // 캐시 키: search:suggest:경복 (소문자 처리)
        when(listOperations.range(eq("search:suggest:경복"), eq(0L), eq(-1L))).thenReturn(cached);

        // when
        List<String> result = searchService.suggest(prefix);

        // then
        assertThat(result).containsExactly("경복궁", "경복궁 야간개장");
        // 캐시 Hit이므로 DB 조회는 절대 호출되면 안 됨
        verify(placeQueryRepository, never()).suggestByPrefix(anyString(), anyInt());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 캐시 Miss — DB + Redis ZSet 통합
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 Miss 시 DB 결과만으로 자동완성 목록을 반환한다 (ZSet 비어있는 경우)")
    void suggest_ReturnsDbResults_WhenCacheMissAndZSetEmpty() {
        // given
        String prefix = "경복";
        List<String> dbResults = List.of("경복궁", "경복로");

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of()); // 캐시 Miss
        when(placeQueryRepository.suggestByPrefix("경복", 5)).thenReturn(dbResults);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null); // ZSet 비어있음

        // when
        List<String> result = searchService.suggest(prefix);

        // then
        assertThat(result).containsExactly("경복궁", "경복로");
    }

    @Test
    @DisplayName("캐시 Miss 시 DB 결과와 Redis ZSet 인기 검색어를 병합하여 반환한다")
    void suggest_MergesDbAndRedisResults_WhenCacheMiss() {
        // given
        String prefix = "경복";

        // DB: 관광지명 2개
        List<String> dbResults = List.of("경복궁", "경복로");

        // Redis ZSet: 인기 검색어 중 "경복"으로 시작하는 것 — "경복궁 야간개장"은 DB에 없는 신규 항목
        Set<ZSetOperations.TypedTuple<String>> zSetTuples = new HashSet<>();
        zSetTuples.add(mockTuple("경복궁", 100.0));          // DB와 중복 → 제거됨
        zSetTuples.add(mockTuple("경복궁 야간개장", 80.0));   // DB에 없는 인기 검색어 → 추가됨
        zSetTuples.add(mockTuple("남산타워", 70.0));          // prefix 미매칭 → 제외됨

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of()); // 캐시 Miss
        when(placeQueryRepository.suggestByPrefix("경복", 5)).thenReturn(dbResults);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(zSetTuples);

        // when
        List<String> result = searchService.suggest(prefix);

        // then
        // DB 우선, ZSet 보완: [경복궁, 경복로, 경복궁 야간개장]
        assertThat(result).hasSize(3);
        assertThat(result).contains("경복궁", "경복로", "경복궁 야간개장");
        // 중복 없음 검증: 경복궁이 2번 들어가면 안 됨
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("자동완성 결과는 최대 5개를 넘지 않는다")
    void suggest_ReturnsAtMostFiveResults() {
        // given
        String prefix = "관광";

        // DB: 5개
        List<String> dbResults = List.of("관광지1", "관광지2", "관광지3", "관광지4", "관광지5");

        // Redis ZSet: 추가 후보가 있어도 이미 5개를 채웠으므로 더 추가되면 안 됨
        Set<ZSetOperations.TypedTuple<String>> zSetTuples = new HashSet<>();
        zSetTuples.add(mockTuple("관광지6", 50.0));

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());
        when(placeQueryRepository.suggestByPrefix("관광", 5)).thenReturn(dbResults);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(zSetTuples);

        // when
        List<String> result = searchService.suggest(prefix);

        // then
        assertThat(result).hasSize(5);
        assertThat(result).doesNotContain("관광지6"); // SUGGEST_MAX_SIZE 초과분은 제외
    }

    @Test
    @DisplayName("prefix 앞뒤 공백은 정규화 후 처리된다")
    void suggest_NormalizesPrefixBeforeQuery() {
        // given
        String prefixWithSpaces = "  경복  ";

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(eq("search:suggest:경복"), anyLong(), anyLong())).thenReturn(List.of());
        when(placeQueryRepository.suggestByPrefix(eq("경복"), anyInt())).thenReturn(List.of("경복궁"));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null);

        // when
        List<String> result = searchService.suggest(prefixWithSpaces);

        // then
        // 공백 제거 후 "경복"으로 쿼리되었음을 검증
        verify(placeQueryRepository).suggestByPrefix("경복", 5);
        assertThat(result).contains("경복궁");
    }

    @Test
    @DisplayName("prefix 50자 정확히는 유효하다 (경계값 검증)")
    void suggest_AcceptsExactly50CharPrefix() {
        // given
        String prefix = "가".repeat(50); // 경계값: 50자

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());
        when(placeQueryRepository.suggestByPrefix(anyString(), anyInt())).thenReturn(List.of());
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null);

        // when & then — 예외 없이 정상 동작해야 함
        List<String> result = searchService.suggest(prefix);
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /** ZSetOperations.TypedTuple 목 객체를 생성하는 헬퍼 메서드. */
    private ZSetOperations.TypedTuple<String> mockTuple(String value, double score) {
        return new ZSetOperations.TypedTuple<>() {
            @Override
            public String getValue() {
                return value;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(o.getScore() != null ? o.getScore() : 0.0, score);
            }
        };
    }
}
