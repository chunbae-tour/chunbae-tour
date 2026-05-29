package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.AdminShopStatusRequest;
import com.chunbaetour.domain.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 가게 관리 API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수.
 */
@Tag(name = "관리자 가게 관리 (ADMIN)", description = "가게 상태 변경 (/api/v1/admin/shops)")
@Validated
@RestController
@RequestMapping("/api/v1/admin/shops")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopService shopService;

    /**
     * 가게 상태 변경 — ACTIVE ↔ SUSPENDED 전환.
     * CLOSED 가게 변경 및 CLOSED로 변경 불가.
     */
    @Operation(summary = "가게 상태 변경")
    @PatchMapping("/{shopId}/status")
    public ApiResponse<Void> updateShopStatus(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody AdminShopStatusRequest request) {
        shopService.updateShopStatus(shopId, request.status());
        return ApiResponse.success(null);
    }
}
