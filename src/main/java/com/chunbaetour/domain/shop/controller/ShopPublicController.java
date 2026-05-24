package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopQrInfoResponse;
import com.chunbaetour.domain.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게 공개 API (STORY-12).
 * GET /api/v1/shops/{shopId}/qr-info — 비인증 공개.
 * QR 스캔·앱 탐색 등 진입 경로에 무관하게 가게 정보를 누구나 조회 가능.
 * 실제 결제 요청(POST /payments/qr)은 USER 인증 필수 — 이 엔드포인트는 메뉴 확인 단계.
 */
@RestController
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopPublicController {

    private final ShopService shopService;

    /** 가게 공개 정보 + 메뉴 목록 조회 — 비로그인 접근 가능 */
    @GetMapping("/{shopId}/qr-info")
    public ApiResponse<ShopQrInfoResponse> getShopQrInfo(@PathVariable Long shopId) {
        return ApiResponse.success(shopService.getShopQrInfo(shopId));
    }
}
