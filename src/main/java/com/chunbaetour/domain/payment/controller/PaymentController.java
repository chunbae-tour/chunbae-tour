package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.service.ChargeService;
import com.chunbaetour.domain.payment.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 API: 엽전 충전 요청 및 환불 요청 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    /** 엽전 충전 요청 → 포트원 V2 사전등록 후 orderUid 반환 */
    @PostMapping("/charge")
    public ApiResponse<ChargeResponse> charge(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChargeRequest request
    ) {
        return ApiResponse.success(chargeService.charge(userId, idempotencyKey, request));
    }

    /**
     * 환불 요청 생성.
     * orderId = 충전 시 발급된 orderUid (UUID).
     * 환불 요청은 PENDING 상태로 생성되며 관리자 승인(STORY-07) 후 실 환불 처리.
     */
    @PostMapping("/{orderId}/refund")
    public ApiResponse<RefundResponse> requestRefund(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderId,
            @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(refundService.requestRefund(userId, orderId, request));
    }
}
