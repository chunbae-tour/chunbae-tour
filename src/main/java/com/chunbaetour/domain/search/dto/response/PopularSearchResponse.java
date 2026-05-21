package com.chunbaetour.domain.search.dto.response;

import com.chunbaetour.domain.search.type.RankingChangeType;

/**
 * 인기 검색어 단건 응답 DTO.
 * <p>
 * SA API 명세서 {@code GET /search/popular} 응답 형식 기준:
 * <pre>
 * { "rank": 1, "keyword": "제주도", "searchCount": 5420, "changeType": "NEW" }
 * </pre>
 * </p>
 *
 * @param rank        현재 순위 (1 = 1위)
 * @param keyword     인기 검색어 문자열
 * @param searchCount 오늘 집계된 검색 횟수 (Redis ZSet score 기반)
 * @param changeType  이전 순위 대비 변동 타입 ({@link RankingChangeType})
 */
public record PopularSearchResponse(
        int rank,
        String keyword,
        long searchCount,
        RankingChangeType changeType
) {

    /**
     * PopularSearchResponse 생성을 위한 정적 팩토리 메서드.
     *
     * @param rank        현재 순위
     * @param keyword     인기 검색어
     * @param searchCount Redis ZSet score (검색 횟수)
     * @param changeType  순위 변동 타입
     * @return PopularSearchResponse 인스턴스
     */
    public static PopularSearchResponse of(int rank, String keyword, long searchCount, RankingChangeType changeType) {
        return new PopularSearchResponse(rank, keyword, searchCount, changeType);
    }
}
