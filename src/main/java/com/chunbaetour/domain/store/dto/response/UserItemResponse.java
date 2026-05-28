package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDate;

/** GET /api/v1/users/me/items 응답 DTO. itemId=user_items.id, expiresAt=null이면 무기한 */
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
