package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 최근 검색어 서비스 (PHASE 3-2)
 *
 * <p>Redis List 자료구조를 사용하여 사용자별 최근 검색어를 관리한다.
 * Redis 장애 시 비즈니스 흐름을 막지 않도록 Exception을 catch하여 빈 리스트/무시로 처리한다. (Eventually Consistent/Optional 데이터 성격)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentSearchService {

    private final StringRedisTemplate stringRedisTemplate;
    
    private static final String RECENT_SEARCH_KEY_PREFIX = "search:recent:";
    private static final int MAX_RECENT_SEARCHES = 10;
    private static final long TTL_SECONDS = 30 * 24 * 60 * 60L; // 30일

    private static final String SAVE_RECENT_SEARCH_SCRIPT = """
            local key = KEYS[1]
            local keyword = ARGV[1]
            local max = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            redis.call('LREM', key, 0, keyword)
            redis.call('LPUSH', key, keyword)
            redis.call('LTRIM', key, 0, max - 1)
            -- 사용자 활동이 있을 때마다 TTL 30일로 갱신 (활성 유저 데이터 유지 의도)
            redis.call('EXPIRE', key, ttl)
            return 1
            """;

    private static final DefaultRedisScript<Long> REDIS_SCRIPT_SAVE =
            new DefaultRedisScript<>(SAVE_RECENT_SEARCH_SCRIPT, Long.class);

    /**
     * 최근 검색어를 저장한다.
     * <p>중복된 검색어가 있으면 기존 것을 삭제하고 맨 앞으로 올리며, 최대 개수(10개)를 유지한다.</p>
     *
     * @param userId  사용자 ID
     * @param keyword 검색어
     */
    public void saveRecentSearch(Long userId, String keyword) {
        String normalizedKeyword = validateAndNormalize(keyword);

        String key = RECENT_SEARCH_KEY_PREFIX + userId;
        try {
            // Lua 스크립트를 사용하여 원자성 보장 및 네트워크 I/O 1회 최적화
            stringRedisTemplate.execute(
                    REDIS_SCRIPT_SAVE,
                    Collections.singletonList(key),
                    normalizedKeyword,
                    String.valueOf(MAX_RECENT_SEARCHES),
                    String.valueOf(TTL_SECONDS)
            );
        } catch (Exception e) {
            log.warn("Recent search save failed: userId={}", userId, e);
        }
    }

    /**
     * 최근 검색어 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 최근 검색어 목록 (최대 10개, 없으면 빈 리스트)
     */
    public List<String> getRecentSearches(Long userId) {
        String key = RECENT_SEARCH_KEY_PREFIX + userId;
        try {
            // 방어적 프로그래밍: LTRIM이 동작하더라도 명시적으로 MAX 개수만큼만 가져옴
            List<String> results = stringRedisTemplate.opsForList().range(key, 0, MAX_RECENT_SEARCHES - 1);
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Recent search fetch failed: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 특정 검색어를 최근 검색어에서 삭제한다.
     */
    public void deleteRecentSearch(Long userId, String keyword) {
        String normalizedKeyword = validateAndNormalize(keyword);
        
        String key = RECENT_SEARCH_KEY_PREFIX + userId;
        try {
            // 중복 불가 구조(저장 시 LREM)이므로 count=1 이면 충분함 (딱 1건만 삭제)
            stringRedisTemplate.opsForList().remove(key, 1, normalizedKeyword);
        } catch (Exception e) {
            log.warn("Recent search delete failed: userId={}", userId, e);
        }
    }

    /**
     * 모든 최근 검색어를 삭제한다.
     */
    public void deleteAllRecentSearches(Long userId) {
        String key = RECENT_SEARCH_KEY_PREFIX + userId;
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Recent search clear all failed: userId={}", userId, e);
        }
    }

    private String validateAndNormalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }
        String normalized = keyword.strip();
        if (normalized.length() > 50) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }
        return normalized;
    }
}
