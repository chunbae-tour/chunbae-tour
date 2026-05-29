package com.chunbaetour.domain.merchant.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplicationRejectRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.service.AdminMerchantApplicationService;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
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
 * 관리자 상인 신청 처리 API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@Tag(name = "상인 신청 (ADMIN)", description = "상인 신청 목록 조회·승인·거절 (/api/v1/admin/merchant-applications/**)")
@RestController
@RequestMapping("/api/v1/admin/merchant-applications")
@RequiredArgsConstructor
@Validated
public class AdminMerchantApplicationController {

    private final AdminMerchantApplicationService adminMerchantApplicationService;

    /**
     * 상인 신청 목록 조회 (cursor 페이징, status 필터).
     * status=PENDING/APPROVED/REJECTED 모두 허용 — 거절 이력 추적 및 감사로그 목적.
     * 거절된 신청자 재신청 시 거절 사유 확인, 승인된 상인 중복 신청 감사에 활용.
     * 기본값 PENDING.
     */
    @Operation(summary = "상인 신청 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<MerchantApplicationDetailResponse>> getApplications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "PENDING") MerchantApplicationStatus status
    ) {
        return ApiResponse.success(adminMerchantApplicationService.getApplications(cursor, size, status));
    }

    /**
     * 상인 신청 단건 상세 조회 (KAN-182).
     * 운영자가 승인/거절 결정 전 신청 상세 정보 확인용. 응답 DTO는 목록/승인/거절 흐름과 동일하게
     * {@link MerchantApplicationDetailResponse} 재사용 — 본 슬라이스는 DTO 신규 필드 미추가.
     * 거절된 신청 조회 시 rejectReason 필드가 포함된 그대로 노출되어 운영자가 재처리 판단에 활용.
     */
    @Operation(summary = "상인 신청 단건 상세 조회")
    @GetMapping("/{applicationId}")
    public ApiResponse<MerchantApplicationDetailResponse> getApplication(@PathVariable Long applicationId) {
        return ApiResponse.success(adminMerchantApplicationService.getApplication(applicationId));
    }

    @Operation(summary = "상인 신청 승인")
    @PatchMapping("/{applicationId}/approve")
    public ApiResponse<MerchantApplicationDetailResponse> approve(@PathVariable Long applicationId) {
        return ApiResponse.success(adminMerchantApplicationService.approve(applicationId));
    }

    @Operation(summary = "상인 신청 거절")
    @PatchMapping("/{applicationId}/reject")
    public ApiResponse<MerchantApplicationDetailResponse> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody MerchantApplicationRejectRequest request
    ) {
        return ApiResponse.success(adminMerchantApplicationService.reject(applicationId, request.rejectReason()));
    }
}
