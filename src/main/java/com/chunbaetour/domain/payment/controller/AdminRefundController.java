package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.service.AdminRefundService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 환불 처리 API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수로 설정됨.
 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@Validated
public class AdminRefundController {

    private final AdminRefundService adminRefundService;

    /** 환불 목록 조회 (cursor 페이징) */
    @GetMapping
    public ApiResponse<CursorPageResponse<RefundDetailResponse>> getRefunds(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminRefundService.getRefunds(cursor, size));
    }

    /** 환불 승인: 엽전 차감 → 상태 APPROVED → PG 취소 */
    @PatchMapping("/{refundId}/approve")
    public ApiResponse<RefundDetailResponse> approveRefund(
            @PathVariable Long refundId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey
    ) {
        return ApiResponse.success(adminRefundService.approveRefund(refundId, idempotencyKey));
    }

    /** 환불 거절: 상태 REJECTED로 변경 (PG 호출 없음) */
    @PatchMapping("/{refundId}/reject")
    public ApiResponse<RefundDetailResponse> rejectRefund(
            @PathVariable Long refundId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey
    ) {
        return ApiResponse.success(adminRefundService.rejectRefund(refundId, idempotencyKey));
    }
}
