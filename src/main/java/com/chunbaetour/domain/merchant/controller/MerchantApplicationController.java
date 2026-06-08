package com.chunbaetour.domain.merchant.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationResponse;
import com.chunbaetour.domain.merchant.service.MerchantApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 신청 API.
 * POST /api/v1/merchants/apply — USER 권한 필요 (SecurityConfig에서 /merchants/apply만 USER 허용).
 */
@Tag(name = "상인 신청", description = "상인 등록 신청 (POST /api/v1/merchants/apply)")
@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
@Validated
public class MerchantApplicationController {

    private final MerchantApplicationService merchantApplicationService;

    @Operation(summary = "상인 등록 신청")
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantApplicationResponse> apply(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MerchantApplyRequest request
    ) {
        return ApiResponse.success(merchantApplicationService.apply(userId, request));
    }
}
