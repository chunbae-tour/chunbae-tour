package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.QrCodeResponse;
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
 * 상인 QR 코드 발급 API (STORY-12).
 * GET /api/v1/merchants/me/shops/{shopId}/qr — MERCHANT 권한 필요.
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@Tag(name = "QR 코드 (MERCHANT)", description = "내 가게 QR 코드 조회 (/api/v1/merchants/me/shops/{shopId}/qr)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/qr")
@RequiredArgsConstructor
public class MerchantQrController {

    private final ShopService shopService;

    @Operation(summary = "내 가게 QR 코드 조회")
    @GetMapping
    public ApiResponse<QrCodeResponse> getMyQrCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId) {
        return ApiResponse.success(shopService.getMyQrCode(userId, shopId));
    }
}
