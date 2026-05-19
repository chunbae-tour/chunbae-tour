package com.chunbaetour.domain.payment;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ApiResponse<WalletResponse> getMyWallet(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(walletService.getWallet(userId));
    }
}
