package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.service.QrPayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/v1/payments/qr — USER 인증 필수. QR 스캔 후 결제 요청 생성. */
@RestController
@RequestMapping("/api/v1/payments/qr")
@RequiredArgsConstructor
public class QrPayController {

    private final QrPayService qrPayService;

    @PostMapping
    public ApiResponse<QrPayCreateResponse> createQrPayRequest(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody QrPayCreateRequest request) {
        return ApiResponse.success(qrPayService.createQrPayRequest(userId, request));
    }
}
