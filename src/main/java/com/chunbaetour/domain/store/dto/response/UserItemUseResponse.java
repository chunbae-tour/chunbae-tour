package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDateTime;

public record UserItemUseResponse(
        Long itemId,
        Long userId,
        Long productId,
        String productName,
        UserItemStatus status,
        LocalDateTime usedAt,
        Long usedShopId
) {
    public static UserItemUseResponse from(UserItem item) {
        return new UserItemUseResponse(
                item.getId(),
                item.getUserId(),
                item.getProductId(),
                item.getProductName(),
                item.getStatus(),
                item.getUsedAt(),
                item.getUsedShopId()
        );
    }
}
