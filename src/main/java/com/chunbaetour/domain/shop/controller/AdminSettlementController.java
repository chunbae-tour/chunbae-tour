package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.request.SettlementRejectRequest;
import com.chunbaetour.domain.shop.dto.response.AdminSettlementResponse;
import com.chunbaetour.domain.shop.service.AdminSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
@Validated
public class AdminSettlementController {

    private final AdminSettlementService adminSettlementService;

    /** GET /api/v1/admin/settlements — 정산 목록 조회 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CursorPageResponse<AdminSettlementResponse>> getSettlements(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(adminSettlementService.getSettlements(cursor, size));
    }

    /** PATCH /api/v1/admin/settlements/{settlementId}/approve — 정산 승인 */
    @PatchMapping("/{settlementId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> approveSettlement(@PathVariable Long settlementId) {
        adminSettlementService.approveSettlement(settlementId);
        return ApiResponse.success(null);
    }

    /** PATCH /api/v1/admin/settlements/{settlementId}/reject — 정산 거절 */
    @PatchMapping("/{settlementId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> rejectSettlement(
            @PathVariable Long settlementId,
            @RequestBody(required = false) SettlementRejectRequest request) {
        String reason = request != null ? request.reason() : null;
        adminSettlementService.rejectSettlement(settlementId, reason);
        return ApiResponse.success(null);
    }
}
