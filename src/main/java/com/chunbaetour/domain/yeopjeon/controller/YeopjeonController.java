package com.chunbaetour.domain.yeopjeon.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/yeopjeon")
@RequiredArgsConstructor
public class YeopjeonController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ApiResponse<WalletBalanceResponse> getMyWallet(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(walletService.getWallet(userId));
    }
}
