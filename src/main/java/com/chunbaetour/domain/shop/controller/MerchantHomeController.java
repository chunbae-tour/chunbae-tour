package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.service.MerchantHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants/me/home")
@RequiredArgsConstructor
public class MerchantHomeController {

    private final MerchantHomeService merchantHomeService;

    @GetMapping
    public ApiResponse<MerchantHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(merchantHomeService.getHome(userId));
    }
}
