package com.chunbaetour.domain.report.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.report.dto.request.MerchantReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportStatusUpdateRequest;
import com.chunbaetour.domain.report.dto.response.PendingCountResponse;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.dto.response.ReportResolveResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 조회/처리 API (KAN-91, KAN-92).
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@Tag(name = "신고 (관리자)", description = "신고 목록 조회·처리 (/api/v1/admin/reports/**)")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "미처리 신고 건수 조회")
    @GetMapping("/pending-count")
    public ApiResponse<PendingCountResponse> getPendingCount() {
        return ApiResponse.success(reportService.getPendingCount());
    }

    /**
     * 신고 목록 조회 (cursor 페이징).
     *
     * @param status null = 전체, "PENDING" / "RESOLVED" / "DISMISSED" = 상태 필터
     * @param cursor Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size   페이지 크기 (1~100, 기본 20)
     */
    @Operation(summary = "신고 목록 조회 (status·targetType·reason·reportedUserId 필터)")
    @GetMapping
    public ApiResponse<CursorPageResponse<ReportResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) @Positive Long reportedUserId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                reportService.getReports(status, targetType, reason, reportedUserId, cursor, size));
    }

    /**
     * 신고 상세 조회.
     *
     * @param reportId 신고 ID
     */
    @Operation(summary = "신고 상세 조회")
    @GetMapping("/{reportId}")
    public ApiResponse<ReportDetailResponse> getReport(@Positive @PathVariable Long reportId) {
        return ApiResponse.success(reportService.getReport(reportId));
    }

    /**
     * 콘텐츠 신고 처리 (KAN-92).
     * targetType이 POST·COMMENT·REVIEW·USER인 신고에만 사용.
     * MERCHANT 신고에 이 엔드포인트 사용 시 REPORT_WRONG_ENDPOINT 에러.
     *
     * @param reportId 처리할 신고 ID
     * @param adminId  인증된 관리자 계정
     * @param request  처리 요청 (action, adminNote)
     */
    @Operation(summary = "콘텐츠 신고 처리")
    @PostMapping("/{reportId}/resolve")
    public ApiResponse<ReportResolveResponse> resolveReport(
            @Positive @PathVariable Long reportId,
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody ReportResolveRequest request
    ) {
        return ApiResponse.success(reportService.resolveReport(reportId, adminId, request));
    }

    /**
     * 가게 신고 처리 (KAN-92).
     * targetType이 MERCHANT인 신고에만 사용.
     * 콘텐츠 신고에 이 엔드포인트 사용 시 REPORT_WRONG_ENDPOINT 에러.
     *
     * @param reportId 처리할 신고 ID
     * @param adminId  인증된 관리자 ID
     * @param request  처리 요청 (HIDE_SHOP·REVOKE_MERCHANT·DISMISS, adminNote)
     */
    @Operation(summary = "가게 신고 처리")
    @PostMapping("/{reportId}/resolve/merchant")
    public ApiResponse<ReportResolveResponse> resolveMerchantReport(
            @Positive @PathVariable Long reportId,
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody MerchantReportResolveRequest request
    ) {
        return ApiResponse.success(
                reportService.resolveMerchantReport(reportId, adminId, request));
    }

    /**
     * 신고 상태 정정 (관리자 오판 정정).
     * RESOLVED → DISMISSED 만 허용. 콘텐츠 복원 + 누적 카운트 자동 감소.
     *
     * @param reportId 정정할 신고 ID
     * @param adminId  인증된 관리자 계정
     * @param request  정정 요청 (status=DISMISSED, adminNote)
     */
    @Operation(summary = "신고 상태 정정 (오판 정정)")
    @PatchMapping("/{reportId}/status")
    public ApiResponse<ReportResolveResponse> updateReportStatus(
            @Positive @PathVariable Long reportId,
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody ReportStatusUpdateRequest request
    ) {
        return ApiResponse.success(reportService.updateReportStatus(reportId, adminId, request));
    }
}
