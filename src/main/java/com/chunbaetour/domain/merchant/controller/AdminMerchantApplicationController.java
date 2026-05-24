package com.chunbaetour.domain.merchant.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplicationRejectRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.service.AdminMerchantApplicationService;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
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
 * 관리자 상인 신청 처리 API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@RestController
@RequestMapping("/api/v1/admin/merchant-applications")
@RequiredArgsConstructor
@Validated
public class AdminMerchantApplicationController {

    private final AdminMerchantApplicationService adminMerchantApplicationService;

    /** 상인 신청 목록 조회 (cursor 페이징, status 필터) */
    @GetMapping
    public ApiResponse<CursorPageResponse<MerchantApplicationDetailResponse>> getApplications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "PENDING") MerchantApplicationStatus status
    ) {
        return ApiResponse.success(adminMerchantApplicationService.getApplications(cursor, size, status));
    }

    /** 상인 신청 승인: application APPROVED → user MERCHANT → Shop 생성 */
    @PatchMapping("/{applicationId}/approve")
    public ApiResponse<MerchantApplicationDetailResponse> approve(@PathVariable Long applicationId) {
        return ApiResponse.success(adminMerchantApplicationService.approve(applicationId));
    }

    /** 상인 신청 거절: 거절 사유 필수 입력 — 상인이 재신청 기준 파악 가능해야 함 */
    @PatchMapping("/{applicationId}/reject")
    public ApiResponse<MerchantApplicationDetailResponse> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody MerchantApplicationRejectRequest request
    ) {
        return ApiResponse.success(adminMerchantApplicationService.reject(applicationId, request.rejectReason()));
    }
}
