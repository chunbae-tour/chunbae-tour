package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final ChargeService chargeService;

    @PostMapping("/charge")
    public ApiResponse<ChargeResponse> charge(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChargeRequest request
    ) {
        return ApiResponse.success(chargeService.charge(userId, idempotencyKey, request));
    }
}
