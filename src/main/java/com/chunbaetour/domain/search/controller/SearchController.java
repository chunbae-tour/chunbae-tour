package com.chunbaetour.domain.search.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.PopularSearchResponse;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.chunbaetour.domain.search.service.PopularSearchService;
import com.chunbaetour.domain.search.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

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
@Validated
public class SearchController {

    private final PopularSearchService popularSearchService;
    private final SearchService searchService;

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

    /**
     * 관광지 키워드 검색.
     * <p>
     * SA: {@code GET /api/v1/search/places}<br>
     * 인증: 불필요(❌)<br>
     * 설명: 카테고리, 지역 필터를 지원하며, 유효한 검색어가 입력된 경우 인기 검색어 점수를 집계한다.
     * </p>
     *
     * @param q        검색어 (옵션)
     * @param category 카테고리 (옵션)
     * @param region   지역 (옵션)
     * @param cursor   커서 아이디 (옵션, 이전 페이지의 마지막 placeId)
     * @param size     페이지 사이즈 (기본값 10)
     * @return 200 OK + 커서 페이지네이션이 적용된 관광지 목록
     */
    @GetMapping("/places")
    public ApiResponse<CursorPageResponse<SearchPlaceResponse>> searchPlaces(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "category", required = false) PlaceCategory category,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        String clientIp = request.getRemoteAddr();
        CursorPageResponse<SearchPlaceResponse> response = searchService.searchPlaces(q, category, region, cursor, size, clientIp);
        return ApiResponse.success(response);
    }
}
