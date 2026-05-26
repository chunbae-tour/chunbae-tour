package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecentSearchServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private RecentSearchService recentSearchService;

    @Test
    @DisplayName("최근 검색어 저장 - 정상 동작 및 Lua 스크립트 실행 검증")
    void saveRecentSearch_Success() {
        // given
        Long userId = 1L;
        String keyword = "제주도";

        // when
        recentSearchService.saveRecentSearch(userId, keyword);

        // then
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(Collections.singletonList("search:recent:1")),
                eq(keyword),
                eq("10"),
                eq(String.valueOf(30 * 24 * 60 * 60L))
        );
    }

    @Test
    @DisplayName("최근 검색어 저장 - Redis 장애 발생 시 예외 미전파 (Fail-Open)")
    void saveRecentSearch_RedisFailure_FailOpen() {
        // given
        Long userId = 1L;
        String keyword = "제주도";
        given(stringRedisTemplate.execute(any(RedisScript.class), any(), anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("Redis connection error"));

        // when & then (예외가 발생하지 않아야 함)
        recentSearchService.saveRecentSearch(userId, keyword);
        verify(stringRedisTemplate).execute(any(RedisScript.class), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("최근 검색어 저장 - 공백 키워드 검증 실패")
    void saveRecentSearch_BlankKeyword_ThrowsException() {
        // given
        Long userId = 1L;
        String blankKeyword = "   ";

        // when & then
        assertThatThrownBy(() -> recentSearchService.saveRecentSearch(userId, blankKeyword))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
    }

    @Test
    @DisplayName("최근 검색어 저장 - 너무 긴 키워드 검증 실패")
    void saveRecentSearch_TooLongKeyword_ThrowsException() {
        // given
        Long userId = 1L;
        String longKeyword = "A".repeat(51);

        // when & then
        assertThatThrownBy(() -> recentSearchService.saveRecentSearch(userId, longKeyword))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_KEYWORD_TOO_LONG);
    }

    @Test
    @DisplayName("최근 검색어 조회 - 정상 조회")
    void getRecentSearches_Success() {
        // given
        Long userId = 1L;
        List<String> expectedList = List.of("제주도", "경복궁");
        given(stringRedisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("search:recent:1", 0, 9)).willReturn(expectedList);

        // when
        List<String> result = recentSearchService.getRecentSearches(userId);

        // then
        assertThat(result).isEqualTo(expectedList);
    }

    @Test
    @DisplayName("최근 검색어 조회 - 결과가 없을 때 빈 리스트 반환")
    void getRecentSearches_NullResult_ReturnsEmptyList() {
        // given
        Long userId = 1L;
        given(stringRedisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("search:recent:1", 0, 9)).willReturn(null);

        // when
        List<String> result = recentSearchService.getRecentSearches(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최근 검색어 조회 - Redis 장애 시 빈 리스트 반환 (Fail-Open)")
    void getRecentSearches_RedisFailure_ReturnsEmptyList() {
        // given
        Long userId = 1L;
        given(stringRedisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("search:recent:1", 0, 9))
                .willThrow(new RuntimeException("Redis connection error"));

        // when
        List<String> result = recentSearchService.getRecentSearches(userId);

        // then
        assertThat(result).isEmpty();
        verify(listOperations).range("search:recent:1", 0, 9);
    }

    @Test
    @DisplayName("최근 검색어 단건 삭제 - 너무 긴 키워드 검증 실패")
    void deleteRecentSearch_TooLongKeyword_ThrowsException() {
        // given
        Long userId = 1L;
        String longKeyword = "A".repeat(51);

        // when & then
        assertThatThrownBy(() -> recentSearchService.deleteRecentSearch(userId, longKeyword))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_KEYWORD_TOO_LONG);
    }

    @Test
    @DisplayName("최근 검색어 단건 삭제 - 정상 동작")
    void deleteRecentSearch_Success() {
        // given
        Long userId = 1L;
        String keyword = "제주도";
        given(stringRedisTemplate.opsForList()).willReturn(listOperations);

        // when
        recentSearchService.deleteRecentSearch(userId, keyword);

        // then
        verify(listOperations).remove("search:recent:1", 1, keyword);
    }

    @Test
    @DisplayName("최근 검색어 단건 삭제 - 공백 키워드 검증 실패")
    void deleteRecentSearch_BlankKeyword_ThrowsException() {
        // given
        Long userId = 1L;
        String blankKeyword = "   ";

        // when & then
        assertThatThrownBy(() -> recentSearchService.deleteRecentSearch(userId, blankKeyword))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
    }

    @Test
    @DisplayName("최근 검색어 단건 삭제 - Redis 장애 시 예외 미전파 (Fail-Open)")
    void deleteRecentSearch_RedisFailure_FailOpen() {
        // given
        Long userId = 1L;
        String keyword = "제주도";
        given(stringRedisTemplate.opsForList()).willReturn(listOperations);
        willThrow(new RuntimeException("Redis connection error"))
                .given(listOperations).remove("search:recent:1", 1, keyword);

        // when & then (예외가 발생하지 않아야 함)
        recentSearchService.deleteRecentSearch(userId, keyword);
        verify(listOperations).remove("search:recent:1", 1, keyword);
    }

    @Test
    @DisplayName("최근 검색어 전체 삭제 - 정상 동작")
    void deleteAllRecentSearches_Success() {
        // given
        Long userId = 1L;

        // when
        recentSearchService.deleteAllRecentSearches(userId);

        // then
        verify(stringRedisTemplate).delete("search:recent:1");
    }

    @Test
    @DisplayName("최근 검색어 전체 삭제 - Redis 장애 시 예외 미전파 (Fail-Open)")
    void deleteAllRecentSearches_RedisFailure_FailOpen() {
        // given
        Long userId = 1L;
        willThrow(new RuntimeException("Redis connection error"))
                .given(stringRedisTemplate).delete("search:recent:1");

        // when & then (예외가 발생하지 않아야 함)
        recentSearchService.deleteAllRecentSearches(userId);
        verify(stringRedisTemplate).delete("search:recent:1");
    }
}
