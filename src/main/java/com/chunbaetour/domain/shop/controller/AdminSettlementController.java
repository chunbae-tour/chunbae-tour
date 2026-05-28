package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.request.SettlementRejectRequest;
import com.chunbaetour.domain.shop.dto.response.AdminSettlementResponse;
import com.chunbaetour.domain.shop.service.AdminSettlementService;
import com.chunbaetour.domain.shop.type.SettlementStatus;
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
 * 관리자 정산 처리 컨트롤러.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@Tag(name = "정산 (관리자)", description = "정산 목록 조회·승인·거절 (/api/v1/admin/settlements/**)")
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
@Validated
public class AdminSettlementController {

    private final AdminSettlementService adminSettlementService;

    @Operation(summary = "정산 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminSettlementResponse>> getSettlements(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) SettlementStatus status) {
        return ApiResponse.success(adminSettlementService.getSettlements(cursor, size, status));
    }

    @Operation(summary = "정산 승인")
    @PatchMapping("/{settlementId}/approve")
    public ApiResponse<Void> approveSettlement(@PathVariable Long settlementId) {
        adminSettlementService.approveSettlement(settlementId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "정산 거절")
    @PatchMapping("/{settlementId}/reject")
    public ApiResponse<Void> rejectSettlement(
            @PathVariable Long settlementId,
            @RequestBody @Valid SettlementRejectRequest request) {
        adminSettlementService.rejectSettlement(settlementId, request.reason());
        return ApiResponse.success(null);
    }
}
