package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.ShopWallet;
import java.time.LocalDateTime;

public record ShopWalletResponse(
        Long shopId,
        long balance,
        LocalDateTime updatedAt
) {
    public static ShopWalletResponse from(ShopWallet wallet) {
        // updatedAt: 첫 생성 후 수익 적립 전에는 null일 수 있으므로 createdAt을 fallback으로 사용
        LocalDateTime updatedAt = wallet.getUpdatedAt() != null
                ? wallet.getUpdatedAt()
                : wallet.getCreatedAt();
        return new ShopWalletResponse(
                wallet.getShopId(),
                wallet.getBalance(),
                updatedAt
        );
    }
}
