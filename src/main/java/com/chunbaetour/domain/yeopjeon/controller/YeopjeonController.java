package com.chunbaetour.domain.yeopjeon.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.YeopjeonHistoryResponse;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import com.chunbaetour.domain.yeopjeon.service.YeopjeonHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/yeopjeon")
@RequiredArgsConstructor
public class YeopjeonController {

    private final WalletService walletService;
    private final YeopjeonHistoryService yeopjeonHistoryService;

    @GetMapping("/balance")
    public ApiResponse<WalletBalanceResponse> getMyWallet(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(walletService.getWallet(userId));
    }

    @GetMapping("/histories")
    public ApiResponse<CursorPageResponse<YeopjeonHistoryResponse>> getHistories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(yeopjeonHistoryService.getHistories(userId, cursor, size));
    }
}
