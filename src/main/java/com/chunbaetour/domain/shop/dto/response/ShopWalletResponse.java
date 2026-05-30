package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.ShopWallet;
import java.time.LocalDateTime;

public record ShopWalletResponse(
        Long shopId,
        long balance,
        LocalDateTime updatedAt
) {
    public static ShopWalletResponse from(ShopWallet wallet) {
        return new ShopWalletResponse(
                wallet.getShopId(),
                wallet.getBalance(),
                wallet.getUpdatedAt()
        );
    }
}
