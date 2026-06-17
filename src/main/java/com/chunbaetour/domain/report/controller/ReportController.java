package com.chunbaetour.domain.report.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 접수·내 신고 내역 조회 API (KAN-90).
 *
 * <p>USER 전용 — ADMIN은 신고 처리자(KAN-91/92)이며 신고자가 아니다.
 * SecurityConfig에서 {@code /api/v1/reports/** → hasRole("USER")} 로 강제.
 */
@Tag(name = "신고 (USER)", description = "신고 접수·내 신고 내역 조회 (/api/v1/reports/**)")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "신고 접수")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportCreateResponse> create(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody ReportCreateRequest request) {
        return ApiResponse.success(reportService.create(accountId, request));
    }

    @Operation(summary = "내 신고 내역 목록 조회")
    @GetMapping("/me")
    public ApiResponse<CursorPageResponse<MyReportResponse>> getMyReports(
            @AuthenticationPrincipal Long accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(reportService.getMyReports(accountId, cursor, size));
    }

    @Operation(summary = "내 신고 단건 조회")
    @GetMapping("/{reportId}")
    public ApiResponse<MyReportResponse> getMyReport(
            @AuthenticationPrincipal Long accountId,
            @Positive @PathVariable Long reportId) {
        return ApiResponse.success(reportService.getMyReport(reportId, accountId));
    }
}
