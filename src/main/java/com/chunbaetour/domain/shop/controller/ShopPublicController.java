package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopInfoResponse;
import com.chunbaetour.domain.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게 공개 API (STORY-12).
 * GET /api/v1/shops/{shopId} — 비인증 공개.
 * QR 스캔·앱 탐색 등 진입 경로 무관하게 가게 정보를 누구나 조회 가능.
 * 실제 결제 요청(POST /payments/qr)은 USER 인증 필수 — 이 엔드포인트는 메뉴 확인 단계.
 */
@Tag(name = "가게 (공개)", description = "가게 공개 정보·메뉴 조회 (/api/v1/shops/**)")
@Validated
@RestController
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopPublicController {

    private final ShopService shopService;

    /**
     * 가게 공개 정보 + 메뉴 목록 조회 — 비로그인 접근 가능.
     *
     * <p>QR 스캔 후 결제창 진입 시 가게명·메뉴를 fetch하는 용도.
     * 로그인 상태에서 토큰이 만료된 경우에도 차단되지 않아야 하므로
     * {@link com.chunbaetour.domain.auth.security.JwtAuthenticationFilter} PUBLIC_PATH_PATTERNS에
     * {@code /api/v1/shops/*} 등록 필수 — 누락 시 만료 토큰 보유 유저가 결제창 접근 불가.
     */
    @Operation(summary = "가게 공개 정보 조회")
    @GetMapping("/{shopId}")
    public ApiResponse<ShopInfoResponse> getShopInfo(@PathVariable @Positive Long shopId) {
        return ApiResponse.success(shopService.getShopInfo(shopId));
    }
}
