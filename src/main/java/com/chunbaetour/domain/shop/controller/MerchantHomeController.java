package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.service.MerchantHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 홈 대시보드 API.
 * 오늘 매출 합계와 최근 QR 결제 목록을 조회한다.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/home")
@RequiredArgsConstructor
public class MerchantHomeController {

    private final MerchantHomeService merchantHomeService;

    /**
     * 내 상인 홈 대시보드를 조회한다.
     *
     * @param userId 인증된 상인 사용자 ID
     * @return 오늘 매출 합계와 최근 결제 목록
     */
    @GetMapping
    public ApiResponse<MerchantHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(merchantHomeService.getHome(userId));
    }
}
