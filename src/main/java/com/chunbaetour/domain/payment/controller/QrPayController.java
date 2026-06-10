package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.QrPayConfirmRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayStatusResponse;
import com.chunbaetour.domain.payment.service.QrPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR 결제 API.
 * POST /api/v1/payments/qr — USER 인증 필수. QR 스캔 후 결제 요청 생성.
 * PATCH /api/v1/payments/qr/{payRequestId}/confirm — MERCHANT 인증 필수. 결제 승인/거절.
 */
@Tag(name = "QR 결제", description = "QR 결제 요청(USER)·승인(MERCHANT) (/api/v1/payments/qr/**)")
@RestController
@RequestMapping("/api/v1/payments/qr")
@RequiredArgsConstructor
public class QrPayController {

    private final QrPayService qrPayService;

    @Operation(summary = "QR 결제 요청 생성 (USER)")
    @PostMapping
    public ApiResponse<QrPayCreateResponse> createQrPayRequest(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody QrPayCreateRequest request) {
        return ApiResponse.success(qrPayService.createQrPayRequest(userId, request));
    }

    @Operation(summary = "QR 결제 상태 폴링 (USER)", description = "푸시 미도달 시 결제 완료 여부 확인 수단")
    @GetMapping("/{payRequestId}/status")
    public ApiResponse<QrPayStatusResponse> getQrPayStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable String payRequestId) {
        return ApiResponse.success(qrPayService.getQrPayStatus(userId, payRequestId));
    }

    @Operation(summary = "QR 결제 승인 (MERCHANT)")
    @PatchMapping("/{payRequestId}/confirm")
    public ApiResponse<Void> confirmQrPayRequest(
            @AuthenticationPrincipal Long merchantUserId,
            @PathVariable String payRequestId,
            @Valid @RequestBody QrPayConfirmRequest request) {
        qrPayService.confirmQrPayRequest(merchantUserId, payRequestId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "QR 결제 요청 취소 (USER)",
            description = "본인 소유 PENDING 요청만 취소. 5분 자동 만료 전 즉시 해제·재결제 가능 (KAN-252)")
    @PostMapping("/{payRequestId}/cancel")
    public ApiResponse<Void> cancelQrPayRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable String payRequestId) {
        qrPayService.cancelQrPayRequest(userId, payRequestId);
        return ApiResponse.success(null);
    }
}
