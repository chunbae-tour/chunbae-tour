package com.chunbaetour.domain.admin.dashboard.controller;

import com.chunbaetour.domain.admin.dashboard.dto.response.AdminDashboardResponse;
import com.chunbaetour.domain.admin.dashboard.service.AdminDashboardService;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 대시보드 API (KAN-181, Admin Epic KAN-177 S03).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수. 클래스 {@link PreAuthorize}는
 * S02에서 활성화된 {@code @EnableMethodSecurity}로 동작하는 이중 방어선.
 *
 * <p>조회 endpoint라 상태 전이가 없어 {@code @LogAdminAction} 미부착.
 */
@Tag(name = "대시보드 (ADMIN)", description = "운영자 대시보드 카운트 패널 (/api/v1/admin/dashboard)")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "대시보드 카운트 요약 (사용자 통계 1차)")
    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(adminDashboardService.getSummary());
    }
}
