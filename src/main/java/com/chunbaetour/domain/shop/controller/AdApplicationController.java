package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.AdApplicationRequest;
import com.chunbaetour.domain.shop.dto.response.AdApplicationResponse;
import com.chunbaetour.domain.shop.service.AdApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 광고 신청 컨트롤러.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수로 설정됨.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/ads")
@RequiredArgsConstructor
public class AdApplicationController {

    private final AdApplicationService adApplicationService;

    /** POST /api/v1/merchants/me/ads — 광고 신청 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdApplicationResponse> applyAd(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdApplicationRequest request) {
        return ApiResponse.success(adApplicationService.applyAd(userId, request));
    }
}
