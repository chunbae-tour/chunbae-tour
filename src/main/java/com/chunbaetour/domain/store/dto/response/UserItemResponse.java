package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDate;

/**
 * Response DTO for GET /api/v1/users/me/items.
 *
 * @param itemId user_items.id
 * @param orderId order id that created the item
 * @param productId purchased product id
 * @param productName product name snapshot at purchase time
 * @param status current user item status
 * @param expiresAt expiration date, or null when the item never expires
 */
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
