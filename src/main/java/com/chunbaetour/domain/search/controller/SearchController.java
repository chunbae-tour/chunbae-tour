package com.chunbaetour.domain.search.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.search.dto.response.PopularSearchResponse;
import com.chunbaetour.domain.search.service.PopularSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검색 도메인 컨트롤러.
 * <p>
 * Base URL: {@code /api/v1/search}
 * 현재 구현 범위: 2-1 인기 검색어 조회 ({@code GET /search/popular})
 * </p>
 *
 * <p>
 * SA API 명세서 §3. 검색(Search) 기준:
 * 인증 불필요(❌) 공개 API이므로 {@code permitAll()}로 개방되어 있어야 한다.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final PopularSearchService popularSearchService;

    /**
     * 인기 검색어 TOP 10 조회.
     * <p>
     * SA: {@code GET /search/popular}<br>
     * 인증: 불필요(❌)<br>
     * 캐싱: Redis ZSet 기반 실시간 집계 (TTL 없이 자정 초기화 방식 사용)
     * </p>
     *
     * <p>
     * 응답 예시:
     * <pre>
     * [
     *   { "rank": 1, "keyword": "제주도",      "searchCount": 5420, "changeType": "SAME" },
     *   { "rank": 2, "keyword": "경복궁",      "searchCount": 4310, "changeType": "UP"   },
     *   { "rank": 3, "keyword": "부산 해운대", "searchCount": 3870, "changeType": "NEW"  }
     * ]
     * </pre>
     * </p>
     *
     * @return 200 OK + 인기 검색어 목록 (0건 이상)
     */
    @GetMapping("/popular")
    public ApiResponse<List<PopularSearchResponse>> getPopularKeywords() {
        List<PopularSearchResponse> result = popularSearchService.getPopularKeywords();
        return ApiResponse.success(result);
    }
}
