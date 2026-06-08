package com.chunbaetour.domain.yeopjeon.dto.response;

import com.chunbaetour.domain.yeopjeon.entity.Wallet;

public record WalletBalanceResponse(long balance) {

    public static WalletBalanceResponse from(Wallet wallet) {
        return new WalletBalanceResponse(wallet.getBalance());
    }
}
