package com.chunbaetour.domain.market.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.service.TraditionalMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전통시장 공개 조회 API.
 * Base URL: {@code /api/v1/traditional-markets}
 */
@Tag(name = "전통시장", description = "전통시장 위치 기반 조회 (/api/v1/traditional-markets/**)")
@RestController
@RequestMapping("/api/v1/traditional-markets")
@RequiredArgsConstructor
@Validated
public class TraditionalMarketController {

    private final TraditionalMarketService traditionalMarketService;

    @SecurityRequirements
    @Operation(summary = "전통시장 주변 조회 (위치 기반)")
    @GetMapping("/nearby")
    public ApiResponse<CursorPageResponse<TraditionalMarketNearbyResponse>> nearby(
            @RequestParam(name = "lat") BigDecimal lat,
            @RequestParam(name = "lng") BigDecimal lng,
            @RequestParam(name = "radius", defaultValue = "3000") @Min(100) @Max(50000) int radius,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        CursorPageResponse<TraditionalMarketNearbyResponse> response =
                traditionalMarketService.findNearby(lat, lng, radius, cursor, size);
        return ApiResponse.success(response);
    }
}
