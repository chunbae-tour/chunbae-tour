package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.MerchantShopStatusRequest;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 가게 API (STORY-10, KAN-213).
 * GET   /api/v1/merchants/me/shops                    — 내 가게 목록 조회 (MERCHANT)
 * GET   /api/v1/merchants/me/shops/{shopId}            — 내 가게 단건 조회 (MERCHANT)
 * PATCH /api/v1/merchants/me/shops/{shopId}            — 내 가게 정보 수정 (MERCHANT)
 * PATCH /api/v1/merchants/me/shops/{shopId}/status     — 내 가게 상태 직접 전환 (MERCHANT)
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@Tag(name = "가게 (MERCHANT)", description = "내 가게 조회·수정·상태 전환 (/api/v1/merchants/me/shops/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops")
@RequiredArgsConstructor
@Validated
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "내 가게 목록 조회")
    @GetMapping
    public ApiResponse<List<ShopResponse>> getMyShops(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(shopService.getMyShops(userId));
    }

    @Operation(summary = "내 가게 단건 조회")
    @GetMapping("/{shopId}")
    public ApiResponse<ShopResponse> getMyShop(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId
    ) {
        return ApiResponse.success(shopService.getMyShop(userId, shopId));
    }

    @Operation(summary = "내 가게 정보 수정")
    @PatchMapping("/{shopId}")
    public ApiResponse<ShopResponse> updateMyShop(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody ShopUpdateRequest request
    ) {
        return ApiResponse.success(shopService.updateMyShop(userId, shopId, request));
    }

    @Operation(summary = "내 가게 상태 직접 전환 (ACTIVE/CLOSED만 허용)")
    @PatchMapping("/{shopId}/status")
    public ApiResponse<Void> updateMyShopStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody MerchantShopStatusRequest request
    ) {
        shopService.updateMyShopStatus(userId, shopId, request.status());
        return ApiResponse.success();
    }
}
