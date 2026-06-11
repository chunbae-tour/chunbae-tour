package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.AdminShopMarketRequest;
import com.chunbaetour.domain.shop.dto.request.AdminShopPlaceRequest;
import com.chunbaetour.domain.shop.dto.request.AdminShopStatusRequest;
import com.chunbaetour.domain.shop.dto.response.AdminShopMarketResponse;
import com.chunbaetour.domain.shop.dto.response.AdminShopPlaceResponse;
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
@Tag(name = "관리자 가게 관리 (ADMIN)", description = "가게 상태 변경·장소/전통시장 연결 (/api/v1/admin/shops)")
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
    @LogAdminAction(actionType = AdminActionType.SHOP_UPDATE,
            targetType = AdminTargetType.SHOP, targetIdVar = "shopId")
    public ApiResponse<Void> updateShopStatus(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody AdminShopStatusRequest request) {
        shopService.updateShopStatus(shopId, request.status());
        return ApiResponse.success(null);
    }

    /**
     * 가게-장소 수동 연결 (KAN-217).
     * placeId=null이면 기존 연결 해제.
     */
    @Operation(summary = "가게 장소 연결 (placeId=null이면 해제)")
    @PatchMapping("/{shopId}/place")
    @LogAdminAction(actionType = AdminActionType.SHOP_UPDATE,
            targetType = AdminTargetType.SHOP, targetIdVar = "shopId")
    public ApiResponse<AdminShopPlaceResponse> updateShopPlace(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody AdminShopPlaceRequest request) {
        return ApiResponse.success(shopService.updateShopPlace(shopId, request.placeId()));
    }

    /**
     * 가게-전통시장 수동 연결 (KAN-268).
     * traditionalMarketId=null이면 기존 연결 해제. 자동 매칭(B9) 미구현 상태의 보정 수단.
     */
    @Operation(summary = "가게 전통시장 연결 (traditionalMarketId=null이면 해제)")
    @PatchMapping("/{shopId}/market")
    @LogAdminAction(actionType = AdminActionType.SHOP_UPDATE,
            targetType = AdminTargetType.SHOP, targetIdVar = "shopId")
    public ApiResponse<AdminShopMarketResponse> updateShopMarket(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody AdminShopMarketRequest request) {
        return ApiResponse.success(shopService.updateShopMarket(shopId, request.traditionalMarketId()));
    }
}
