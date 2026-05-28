package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.service.ShopService;
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
 * 상인 가게 API (STORY-10).
 * GET  /api/v1/merchants/me/shops           — 내 가게 목록 조회 (MERCHANT)
 * GET  /api/v1/merchants/me/shops/{shopId}  — 내 가게 단건 조회 (MERCHANT)
 * PATCH /api/v1/merchants/me/shops/{shopId} — 내 가게 정보 수정 (MERCHANT)
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/shops")
@RequiredArgsConstructor
@Validated
public class ShopController {

    private final ShopService shopService;

    /** 내 가게 목록 조회: 상인이 운영하는 모든 가게 반환 */
    @GetMapping
    public ApiResponse<List<ShopResponse>> getMyShops(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(shopService.getMyShops(userId));
    }

    /** 내 가게 단건 조회: shopId + userId 소유권 검증 */
    @GetMapping("/{shopId}")
    public ApiResponse<ShopResponse> getMyShop(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId
    ) {
        return ApiResponse.success(shopService.getMyShop(userId, shopId));
    }

    /** 내 가게 정보 수정: null 필드는 기존 값 유지 (부분 수정) */
    @PatchMapping("/{shopId}")
    public ApiResponse<ShopResponse> updateMyShop(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody ShopUpdateRequest request
    ) {
        return ApiResponse.success(shopService.updateMyShop(userId, shopId, request));
    }
}
