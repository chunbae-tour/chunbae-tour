package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.AdApplicationRequest;
import com.chunbaetour.domain.shop.dto.request.AdExtendRequest;
import com.chunbaetour.domain.shop.dto.response.AdApplicationResponse;
import com.chunbaetour.domain.shop.service.AdApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
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
@Validated
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

    /**
     * POST /api/v1/merchants/me/ads/{adId}/extend — 광고 연장 (엽전 차감).
     * 기존 광고의 endDate를 변경하고, 연장된 endDate를 포함한 응답을 200 OK로 반환한다.
     */
    @PostMapping("/{adId}/extend")
    public ApiResponse<AdApplicationResponse> extendAd(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long adId,
            @RequestBody @Valid AdExtendRequest request) {
        return ApiResponse.success(adApplicationService.extendAd(userId, adId, request.extensionDays()));
    }
}
