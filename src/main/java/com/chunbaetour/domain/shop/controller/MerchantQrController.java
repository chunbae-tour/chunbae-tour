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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 QR 코드 발급 API (STORY-12, 재발급 KAN-253).
 * GET  /api/v1/merchants/me/shops/{shopId}/qr         — 내 가게 QR 조회.
 * POST /api/v1/merchants/me/shops/{shopId}/qr/reissue — QR 재발급(nonce 회전, 옛 QR 무효화).
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@Tag(name = "QR 코드 (MERCHANT)", description = "내 가게 QR 코드 조회·재발급 (/api/v1/merchants/me/shops/{shopId}/qr)")
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

    @Operation(summary = "내 가게 QR 재발급",
            description = "분실·도용 의심 시 nonce를 회전해 기존 QR을 무효화하고 새 payload를 반환 (KAN-253). ACTIVE 가게만 가능.")
    @PostMapping("/reissue")
    public ApiResponse<QrCodeResponse> reissueMyQrCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId) {
        return ApiResponse.success(shopService.reissueMyQrCode(userId, shopId));
    }
}
