package com.chunbaetour.domain.search.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.search.dto.response.SearchFestivalResponse;
import com.chunbaetour.domain.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "검색 v2", description = "축제 검색 v2 — address/imageUrl 키 사용 (/api/v2/search/**)")
@RestController
@RequestMapping("/api/v2/search")
@RequiredArgsConstructor
@Validated
public class SearchV2Controller {

    private final SearchService searchService;

    /**
     * 축제 검색 v2.
     * <p>
     * {@code GET /api/v2/search/festivals}<br>
     * 응답 키: {@code address}, {@code imageUrl} (v1의 location/thumbnailUrl에서 변경)
     * </p>
     */
    @SecurityRequirements
    @Operation(summary = "축제 검색 v2 (address/imageUrl)")
    @GetMapping("/festivals")
    public ApiResponse<CursorPageResponse<SearchFestivalResponse>> searchFestivals(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(name = "track", defaultValue = "true") boolean track,
            @RequestParam(name = "source", required = false) String source,
            HttpServletRequest request
    ) {
        String clientIp = request.getRemoteAddr();
        return ApiResponse.success(searchService.searchFestivals(q, startDate, endDate, region, cursor, size, clientIp, track));
    }
}
