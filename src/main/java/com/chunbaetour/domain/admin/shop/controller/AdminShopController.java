package com.chunbaetour.domain.admin.shop.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.shop.dto.request.AdminShopUpdateRequest;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopDetailResponse;
import com.chunbaetour.domain.admin.shop.service.AdminShopService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 가게 관리 API (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수 — 메서드 보안은 S02
 * {@code AdminUserController}와 동일하게 {@code @PreAuthorize("hasRole('ADMIN')")}로 이중 보호.
 *
 * <p>PATCH endpoint에 {@link LogAdminAction}을 부착해 S01 audit 인프라가 자동 기록한다 —
 * {@code targetIdVar = "shopId"}로 path 변수에서 결정적으로 대상 id를 추출(S01-FIX). 조회(GET)는 상태 전이가
 * 없어 audit 미부착.
 *
 * <p>bean 이름은 {@code domain/shop/controller}의 기존 {@code AdminShopController}(PATCH /{shopId}/status)와
 * 단순 클래스명이 겹치므로 {@code "adminShopManagementController"}로 명시해 충돌을 피한다.
 */
@Tag(name = "관리자 가게 관리 (ADMIN)", description = "가게 상세·수정 (/api/v1/admin/shops/**)")
@RestController("adminShopManagementController")
@RequestMapping("/api/v1/admin/shops")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopController {

    private final AdminShopService adminShopService;

    @Operation(summary = "가게 단건 상세 조회 (기본 정보 + 인증 + 리뷰 집계)")
    @GetMapping("/{shopId}")
    public ApiResponse<AdminShopDetailResponse> getShop(@PathVariable @Positive Long shopId) {
        return ApiResponse.success(adminShopService.getShop(shopId));
    }

    @Operation(summary = "가게 정보 수정 (status/description/phone/operatingHours partial)")
    @PatchMapping("/{shopId}")
    @LogAdminAction(actionType = AdminActionType.SHOP_UPDATE,
            targetType = AdminTargetType.SHOP,
            targetIdVar = "shopId")
    public ApiResponse<AdminShopDetailResponse> updateShop(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody AdminShopUpdateRequest request) {
        // 전 필드 null인 빈 PATCH는 의미 없는 수정이므로 400으로 거부한다(Bean Validation은 null 필드를 통과시킴).
        // 여기서 예외가 나면 @LogAdminAction Around advice의 pjp.proceed()가 예외를 전파해 record()가 호출되지
        // 않으므로 audit(SHOP_UPDATE) 로그도 남지 않는다 — "실패한 액션은 미기록" 정합.
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(adminShopService.updateShop(shopId, request));
    }
}
