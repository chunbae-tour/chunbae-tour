package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDate;

public record UserItemResponse(
        Long itemId,
        Long orderId,
        Long productId,
        String productName,
        UserItemStatus status,
        LocalDate expiresAt
) {
    public static UserItemResponse from(UserItem item) {
        return new UserItemResponse(
                item.getId(),
                item.getOrderId(),
                item.getProductId(),
                item.getProductName(),
                item.getStatus(),
                item.getExpiresAt()
        );
    }
}
