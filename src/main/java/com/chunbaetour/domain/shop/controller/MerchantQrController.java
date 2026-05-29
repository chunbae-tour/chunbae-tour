package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.QrCodeResponse;
import com.chunbaetour.domain.shop.service.ShopService;
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
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/qr")
@RequiredArgsConstructor
public class MerchantQrController {

    private final ShopService shopService;

    /** 내 가게 QR payload 조회 — 클라이언트가 qrPayload로 QR 이미지 렌더링 */
    @GetMapping
    public ApiResponse<QrCodeResponse> getMyQrCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId) {
        return ApiResponse.success(shopService.getMyQrCode(userId, shopId));
    }
}
