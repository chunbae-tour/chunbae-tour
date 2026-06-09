package com.chunbaetour.domain.search.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.request.RecentSearchRequest;
import com.chunbaetour.domain.search.dto.response.PopularSearchResponse;
import com.chunbaetour.domain.search.dto.response.SearchFestivalV1Response;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.chunbaetour.domain.search.service.PopularSearchService;
import com.chunbaetour.domain.search.service.RecentSearchService;
import com.chunbaetour.domain.search.service.SearchService;
import com.chunbaetour.domain.search.service.SuggestService;
import com.chunbaetour.domain.search.service.IntegratedSearchService;
import com.chunbaetour.domain.search.dto.response.integrated.IntegratedSearchItem;
import com.chunbaetour.domain.search.dto.response.SuggestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * 검색 도메인 컨트롤러.
 * <p>
 * Base URL: {@code /api/v1/search}
 * 현재 구현 범위:
 * 2-1 인기 검색어 조회 ({@code GET /search/popular}),
 * 2-2 관광지 검색 ({@code GET /search/places}),
 * 2-3 축제 검색 ({@code GET /search/festivals}),
 * 2-4 검색어 자동완성 ({@code GET /search/suggest})
 * </p>
 *
 * <p>
 * SA API 명세서 §3. 검색(Search) 기준:
 * 인증 불필요(❌) 공개 API이므로 {@code permitAll()}로 개방되어 있어야 한다.
 * </p>
 */
@Tag(name = "검색", description = "관광지·축제 검색, 자동완성, 인기·최근 검색어 (/api/v1/search/**)")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final PopularSearchService popularSearchService;
    private final SearchService searchService;
    private final SuggestService suggestService;
    private final RecentSearchService recentSearchService;
    private final IntegratedSearchService integratedSearchService;

    @SecurityRequirements
    @Operation(summary = "통합 검색 (장소/가게/메뉴/축제)")
    @GetMapping
    public ApiResponse<CursorPageResponse<IntegratedSearchItem>> searchIntegrated(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "type", defaultValue = "ALL") String type,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        CursorPageResponse<IntegratedSearchItem> response = 
                integratedSearchService.searchIntegrated(q, type, cursor, size);
        return ApiResponse.success(response);
    }

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
    @SecurityRequirements
    @Operation(summary = "인기 검색어 TOP10 조회")
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
    @SecurityRequirements
    @Operation(summary = "관광지 검색")
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

    /**
     * 축제 검색 v1.
     * <p>
     * SA: {@code GET /api/v1/search/festivals}<br>
     * 인증: 불필요(❌)<br>
     * 응답 키: {@code location}, {@code thumbnailUrl} (하위 호환 유지)<br>
     * 신규 키({@code address}/{@code imageUrl})는 {@code /api/v2/search/festivals} 사용.
     * </p>
     *
     * @param q         검색어 (옵션)
     * @param startDate 시작일 필터 (옵션)
     * @param endDate   종료일 필터 (옵션)
     * @param region    지역 (옵션)
     * @param cursor    커서 아이디 (옵션, 이전 페이지의 마지막 festivalId)
     * @param size      페이지 사이즈 (기본값 10)
     * @return 200 OK + 커서 페이지네이션이 적용된 축제 목록
     */
    @SecurityRequirements
    @Operation(summary = "축제 검색 v1 (location/thumbnailUrl)")
    @GetMapping("/festivals")
    public ApiResponse<CursorPageResponse<SearchFestivalV1Response>> searchFestivals(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        String clientIp = request.getRemoteAddr();
        return ApiResponse.success(searchService.searchFestivalsV1(q, startDate, endDate, region, cursor, size, clientIp));
    }

    /**
     * 검색어 자동완성.
     * <p>
     * SA: {@code GET /api/v1/search/suggest?q={prefix}}<br>
     * 인증: 불필요(❌)<br>
     * 설명: prefix(입력 중인 검색어)를 기반으로 관광지명 DB + Redis 인기 검색어 ZSet에서
     * 자동완성 후보를 최대 5개 반환한다. 결과는 Redis String 캐시에 5분간 캐싱된다.
     * </p>
     *
     * <p>
     * SA 응답 예시:
     * <pre>
     * GET /search/suggest?q=경복
     * 200 OK
     * {
     *   "data": ["경복궁", "경복궁역", "경복궁 야간개장"]
     * }
     * </pre>
     * </p>
     *
     * @param q prefix (필수, 1자 이상)
     * @return 200 OK + 자동완성 후보 키워드 목록 (최대 5개)
     * @throws com.chunbaetour.domain.common.error.BusinessException q가 null/blank인 경우 PLACE_005
     * @throws com.chunbaetour.domain.common.error.BusinessException q가 50자를 초과하는 경우 PLACE_006
     */
    @SecurityRequirements
    @Operation(summary = "검색어 자동완성")
    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggest(
            @RequestParam(name = "q") String q
    ) {
        List<SuggestResponse> result = suggestService.suggest(q);
        List<String> keywords = result.stream()
                .map(SuggestResponse::keyword)
                .toList();
        return ApiResponse.success(keywords);
    }

    /**
     * 최근 검색어 저장.
     * <p>
     * SA: {@code POST /api/v1/search}<br>
     * 인증: 필수(USER)<br>
     * 설명: 사용자의 검색 히스토리를 Redis List에 저장한다. (최대 10개)
     * </p>
     *
     * @param userId 인증된 사용자 ID
     * @param request 검색어
     * @return 200 OK
     */
    @Operation(summary = "최근 검색어 저장")
    @PostMapping
    public ApiResponse<Void> saveRecentSearch(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecentSearchRequest request
    ) {
        recentSearchService.saveRecentSearch(userId, request.keyword());
        return ApiResponse.success(null);
    }

    /**
     * 최근 검색어 조회.
     * <p>
     * SA: {@code GET /api/v1/search/recent}<br>
     * 인증: 필수(USER)<br>
     * 설명: 사용자의 최근 검색어 목록을 조회한다. (최대 10개)
     * </p>
     *
     * @param userId 인증된 사용자 ID
     * @return 200 OK + 최근 검색어 목록
     */
    @Operation(summary = "최근 검색어 조회")
    @GetMapping("/recent")
    public ApiResponse<List<String>> getRecentSearches(
            @AuthenticationPrincipal Long userId
    ) {
        List<String> results = recentSearchService.getRecentSearches(userId);
        return ApiResponse.success(results);
    }

    /**
     * 최근 검색어 단건/전체 삭제.
     * <p>
     * SA: {@code DELETE /api/v1/search/recent}<br>
     * 인증: 필수(USER)<br>
     * 설명: keyword 파라미터가 있으면 단건 삭제, 없으면 전체 삭제를 수행한다.
     * </p>
     *
     * @param userId 인증된 사용자 ID
     * @param keyword 삭제할 검색어 (선택)
     * @return 204 No Content
     */
    @Operation(summary = "최근 검색어 삭제 (단건/전체)")
    @DeleteMapping("/recent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecentSearch(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "keyword", required = false) @Size(max = 50, message = "검색어는 최대 50자까지 입력 가능합니다.") String keyword
    ) {
        if (keyword == null) {
            recentSearchService.deleteAllRecentSearches(userId);
        } else {
            recentSearchService.deleteRecentSearch(userId, keyword);
        }
    }
}
