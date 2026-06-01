package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.response.PendingQrPayResponse;
import com.chunbaetour.domain.payment.service.QrPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 QR 결제 대기 목록 API.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수.
 */
@Tag(name = "QR 결제 대기 (MERCHANT)", description = "상인 대기중 QR 결제 목록 조회 (/api/v1/merchants/me/qr-payments/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/qr-payments")
@RequiredArgsConstructor
public class MerchantQrPayController {

    private final QrPayService qrPayService;

    @Operation(summary = "대기중 QR 결제 목록 조회")
    @GetMapping("/pending")
    public ApiResponse<List<PendingQrPayResponse>> getPendingQrPayments(
            @AuthenticationPrincipal Long merchantUserId) {
        return ApiResponse.success(qrPayService.getPendingQrPayments(merchantUserId));
    }
}
