package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopWalletResponse;
import com.chunbaetour.domain.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게 수익 지갑 조회 API.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수.
 */
@Tag(name = "가게 수익 지갑 (MERCHANT)", description = "가게별 수익 지갑 잔액 조회 (/api/v1/merchants/me/shops/{shopId}/wallet)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/wallet")
@RequiredArgsConstructor
public class ShopWalletController {

    private final ShopService shopService;

    @Operation(summary = "가게 수익 지갑 조회")
    @GetMapping
    public ApiResponse<ShopWalletResponse> getShopWallet(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId) {
        return ApiResponse.success(shopService.getShopWallet(userId, shopId));
    }
}
