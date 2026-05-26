package com.chunbaetour.domain.report.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.service.ReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 조회 API (KAN-91).
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final ReportService reportService;

    /**
     * 신고 목록 조회 (cursor 페이징).
     *
     * @param status null = 전체, "PENDING" / "RESOLVED" / "DISMISSED" = 상태 필터
     * @param cursor Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size   페이지 크기 (1~100, 기본 20)
     */
    @GetMapping
    public ApiResponse<CursorPageResponse<ReportResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(reportService.getReports(status, cursor, size));
    }

    /**
     * 신고 상세 조회.
     *
     * @param reportId 신고 ID
     */
    @GetMapping("/{reportId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable Long reportId) {
        return ApiResponse.success(reportService.getReport(reportId));
    }
}
