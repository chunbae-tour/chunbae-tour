package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.request.AdRejectRequest;
import com.chunbaetour.domain.shop.dto.response.AdminAdApplicationResponse;
import com.chunbaetour.domain.shop.service.AdminAdApplicationService;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 광고 신청 처리 컨트롤러.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@Tag(name = "관리자 광고 신청 (ADMIN)", description = "광고 신청 목록 조회, 승인, 거절 (/api/v1/admin/ads)")
@RestController
@RequestMapping("/api/v1/admin/ads")
@RequiredArgsConstructor
@Validated
public class AdminAdApplicationController {

    private final AdminAdApplicationService adminAdApplicationService;

    /** GET /api/v1/admin/ads — 광고 신청 목록 조회 */
    @Operation(summary = "광고 신청 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminAdApplicationResponse>> getApplications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AdApplicationStatus status) {
        return ApiResponse.success(adminAdApplicationService.getApplications(cursor, size, status));
    }

    /** PATCH /api/v1/admin/ads/{adId}/approve — 광고 승인 */
    @Operation(summary = "광고 승인")
    @PatchMapping("/{adId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long adId) {
        adminAdApplicationService.approve(adId);
        return ApiResponse.success(null);
    }

    /** PATCH /api/v1/admin/ads/{adId}/reject — 광고 거절 (사유 필수) */
    @Operation(summary = "광고 거절")
    @PatchMapping("/{adId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long adId,
            @RequestBody @Valid AdRejectRequest request) {
        adminAdApplicationService.reject(adId, request.reason());
        return ApiResponse.success(null);
    }
}
