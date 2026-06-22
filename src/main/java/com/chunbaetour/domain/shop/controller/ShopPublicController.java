package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.ShopInfoResponse;
import com.chunbaetour.domain.shop.dto.response.ShopNoticeResponse;
import com.chunbaetour.domain.shop.service.ShopNoticeService;
import com.chunbaetour.domain.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ShopNoticeService shopNoticeService;

    /**
     * 가게 공개 정보 + 메뉴 목록 조회 — 비로그인 접근 가능.
     *
     * <p>QR 스캔 후 결제창 진입 시 가게명·메뉴를 fetch하는 용도.
     * 로그인 상태에서 토큰이 만료된 경우에도 차단되지 않아야 하므로
     * {@link com.chunbaetour.domain.auth.security.JwtAuthenticationFilter} PUBLIC_PATH_PATTERNS에
     * {@code /api/v1/shops/*} 등록 필수 — 누락 시 만료 토큰 보유 유저가 결제창 접근 불가.
     */
    @SecurityRequirements
    @Operation(summary = "가게 공개 정보 조회")
    @GetMapping("/{shopId}")
    public ApiResponse<ShopInfoResponse> getShopInfo(@PathVariable @Positive Long shopId) {
        return ApiResponse.success(shopService.getShopInfo(shopId));
    }

    /**
     * 가게 공개 공지 목록 조회 — 비로그인 접근 가능 (KAN-323).
     *
     * <p>상인 전용 {@code /merchants/me/shops/{id}/notices}는 소유자 인증이 필요해 일반/비로그인 사용자가
     * 호출할 수 없으므로 별도 공개 경로를 둔다. SUSPENDED 가게는 차단(SHOP_001), CLOSED는 휴무 공지를 위해 허용.
     * SecurityConfig·JwtAuthenticationFilter의 {@code /api/v1/shops/{id}/notices} 공개 패턴과 동기화 필수.
     */
    @SecurityRequirements
    @Operation(summary = "가게 공개 공지 목록 조회 (커서 페이징, 최신순)")
    @GetMapping("/{shopId}/notices")
    public ApiResponse<CursorPageResponse<ShopNoticeResponse>> getShopNotices(
            @PathVariable @Positive Long shopId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(shopNoticeService.getPublicNotices(shopId, cursor, size));
    }
}
