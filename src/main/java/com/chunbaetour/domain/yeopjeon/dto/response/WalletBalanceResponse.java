package com.chunbaetour.domain.yeopjeon.dto.response;

import com.chunbaetour.domain.yeopjeon.entity.Wallet;

public record WalletBalanceResponse(Long userId, long balance) {

    public static WalletBalanceResponse from(Wallet wallet) {
        return new WalletBalanceResponse(wallet.getUserId(), wallet.getBalance());
    }
}
