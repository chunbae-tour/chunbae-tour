package com.chunbaetour.domain.yeopjeon.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.YeopjeonHistoryResponse;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import com.chunbaetour.domain.yeopjeon.service.YeopjeonHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 엽전 잔액·이력 조회 API.
 * <p>
 * API 명세는 /wallets/me, /wallets/me/histories로 정의되어 있으나,
 * /wallets/me가 "내 지갑 전체(잔액+이력)"를 반환하는 것처럼 읽힐 수 있어
 * 명시성을 위해 /yeopjeon/balance, /yeopjeon/histories로 분리 구현.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/yeopjeon")
@RequiredArgsConstructor
@Validated
public class YeopjeonController {

    private final WalletService walletService;
    private final YeopjeonHistoryService yeopjeonHistoryService;

    // 내 엽전 잔액 조회 (명세: GET /wallets/me) -> (yeopjeon/balance) 엔드포인트 수정
    @GetMapping("/balance")
    public ApiResponse<WalletBalanceResponse> getMyWallet(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(walletService.getWallet(userId));
    }

    // 엽전 사용 내역 cursor 페이징 조회 (명세: GET /wallets/me/histories) -> (yeopjeon/histories) 엔드포인트 수정
    @GetMapping("/histories")
    public ApiResponse<CursorPageResponse<YeopjeonHistoryResponse>> getHistories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(yeopjeonHistoryService.getHistories(userId, cursor, size));
    }
}
