package com.chunbaetour.domain.market.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyPageResponse;
import com.chunbaetour.domain.market.service.TraditionalMarketLikeService;
import com.chunbaetour.domain.market.service.TraditionalMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전통시장 공개 조회 API.
 * Base URL: {@code /api/v1/traditional-markets}
 */
@Tag(name = "전통시장", description = "전통시장 위치 기반 조회는 비인증 허용, 찜 추가·취소는 USER 인증 필요 (/api/v1/traditional-markets/**)")
@RestController
@RequestMapping("/api/v1/traditional-markets")
@RequiredArgsConstructor
@Validated
public class TraditionalMarketController {

    private final TraditionalMarketService traditionalMarketService;
    private final TraditionalMarketLikeService traditionalMarketLikeService;

    @SecurityRequirements
    @Operation(summary = "전통시장 주변 조회 (위치 기반)")
    @GetMapping("/nearby")
    public ApiResponse<TraditionalMarketNearbyPageResponse> nearby(
            @RequestParam(name = "lat") @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @RequestParam(name = "lng") @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @RequestParam(name = "radius", defaultValue = "3000") @Min(100) @Max(50000) int radius,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        TraditionalMarketNearbyPageResponse response =
                traditionalMarketService.findNearby(lat, lng, radius, page, size);
        return ApiResponse.success(response);
    }

    @Operation(summary = "전통시장 찜 추가")
    @PostMapping("/{marketId}/like")
    public ApiResponse<Void> addLike(
            @Positive @PathVariable Long marketId,
            @AuthenticationPrincipal Long userId
    ) {
        traditionalMarketLikeService.addLike(userId, marketId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "전통시장 찜 취소")
    @DeleteMapping("/{marketId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeLike(
            @Positive @PathVariable Long marketId,
            @AuthenticationPrincipal Long userId
    ) {
        traditionalMarketLikeService.removeLike(userId, marketId);
        return ApiResponse.success(null);
    }
}
