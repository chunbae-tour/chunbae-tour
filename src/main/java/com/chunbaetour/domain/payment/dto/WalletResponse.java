package com.chunbaetour.domain.payment.dto;

import com.chunbaetour.domain.payment.Wallet;

public record WalletResponse(Long userId, Long balance) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getUserId(), wallet.getBalance());
    }
}
