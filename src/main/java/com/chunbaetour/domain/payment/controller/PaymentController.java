package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.PaymentHistoryResponse;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.service.ChargeService;
import com.chunbaetour.domain.payment.service.RefundService;
import com.chunbaetour.domain.payment.type.RefundStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/** 결제 API: 엽전 충전 요청 및 환불 요청 */
@Tag(name = "결제 (USER)", description = "엽전 충전·환불 요청·환불 취소 (/api/v1/payments/**)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    @Operation(summary = "결제(충전) 내역 조회",
            description = "cursor: 응답의 nextCursor 값을 그대로 전달 (Base64URL 인코딩된 id, 별도 변환 불필요). " +
                    "null이면 첫 페이지. status는 PENDING·COMPLETED·FAILED·CANCELLED·REFUNDED 전체 포함.")
    @GetMapping("/history")
    public ApiResponse<CursorPageResponse<PaymentHistoryResponse>> getPaymentHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(chargeService.getPaymentHistory(userId, cursor, size));
    }

    @Operation(summary = "엽전 충전 요청")
    @PostMapping("/charge")
    public ApiResponse<ChargeResponse> charge(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChargeRequest request
    ) {
        return ApiResponse.success(chargeService.charge(userId, idempotencyKey, request));
    }

    /**
     * 충전 주문 사용자 직접 취소 (KAN-252).
     * orderId = 충전 시 발급된 orderUid (UUID).
     * 본인 소유 PENDING 충전 주문만 취소 가능 — 결제창 완료 전 대기 해제 후 즉시 재시도용.
     */
    @Operation(summary = "충전 주문 취소", description = "본인 소유 PENDING 충전 주문만 취소 (결제창 완료 전)")
    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCharge(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderId
    ) {
        chargeService.cancelCharge(userId, orderId);
    }

    /**
     * 환불 요청 생성.
     * orderId = 충전 시 발급된 orderUid (UUID).
     * 환불 요청은 PENDING 상태로 생성되며 관리자 승인(STORY-07) 후 실 환불 처리.
     */
    @Operation(summary = "환불 요청 생성")
    @PostMapping("/{orderId}/refund")
    public ApiResponse<RefundResponse> requestRefund(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(refundService.requestRefund(userId, orderId, request));
    }

    @Operation(summary = "환불 요청 취소")
    @PatchMapping("/refund/{refundId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRefund(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long refundId
    ) {
        refundService.cancelRefund(userId, refundId);
    }

    /**
     * 사용자 환불 내역 조회 (KAN-115).
     * status 생략 시 전체 상태 조회. cursor 기반 페이징.
     */
    @Operation(summary = "내 환불 내역 조회")
    @GetMapping("/refunds")
    public ApiResponse<CursorPageResponse<UserRefundResponse>> getUserRefundHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(refundService.getUserRefundHistory(userId, status, cursor, size));
    }
}
