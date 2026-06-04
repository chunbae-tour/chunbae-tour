package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.request.ShopNoticeCreateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopNoticeResponse;
import com.chunbaetour.domain.shop.service.ShopNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게 공지 API (KAN-213).
 * POST   /api/v1/merchants/me/shops/{shopId}/notices         — 공지 등록 (MERCHANT)
 * GET    /api/v1/merchants/me/shops/{shopId}/notices         — 공지 목록 조회 (MERCHANT)
 * DELETE /api/v1/merchants/me/shops/{shopId}/notices/{noticeId} — 공지 삭제 (MERCHANT)
 */
@Tag(name = "가게 공지 (MERCHANT)", description = "가게 공지 등록·조회·삭제 (/api/v1/merchants/me/shops/{shopId}/notices/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/notices")
@RequiredArgsConstructor
@Validated
public class ShopNoticeController {

    private final ShopNoticeService shopNoticeService;

    @Operation(summary = "가게 공지 등록")
    @PostMapping
    public ApiResponse<ShopNoticeResponse> createNotice(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody ShopNoticeCreateRequest request
    ) {
        return ApiResponse.success(shopNoticeService.createNotice(userId, shopId, request));
    }

    @Operation(summary = "가게 공지 목록 조회 (커서 페이징, 최신순)")
    @GetMapping
    public ApiResponse<CursorPageResponse<ShopNoticeResponse>> getNotices(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(shopNoticeService.getNotices(userId, shopId, cursor, size));
    }

    @Operation(summary = "가게 공지 삭제")
    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> deleteNotice(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @PathVariable Long noticeId
    ) {
        shopNoticeService.deleteNotice(userId, shopId, noticeId);
        return ApiResponse.success();
    }
}
