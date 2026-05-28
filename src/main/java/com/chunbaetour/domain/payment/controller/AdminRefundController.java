package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.service.AdminRefundService;
import com.chunbaetour.domain.payment.dto.request.RefundRejectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 관리자 환불 처리 API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@Tag(name = "환불 (관리자)", description = "환불 목록 조회·승인·거절 (/api/v1/admin/refunds/**)")
@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@Validated
public class AdminRefundController {

    private final AdminRefundService adminRefundService;

    @Operation(summary = "환불 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<RefundDetailResponse>> getRefunds(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminRefundService.getRefunds(cursor, size));
    }

    @Operation(summary = "환불 승인")
    @PatchMapping("/{refundId}/approve")
    public ApiResponse<RefundDetailResponse> approveRefund(@PathVariable Long refundId) {
        return ApiResponse.success(adminRefundService.approveRefund(refundId));
    }

    @Operation(summary = "환불 거절")
    @PatchMapping("/{refundId}/reject")
    public ApiResponse<RefundDetailResponse> rejectRefund(
            @PathVariable Long refundId,
            @RequestBody(required = false) RefundRejectRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.success(adminRefundService.rejectRefund(refundId, reason));
    }
}
