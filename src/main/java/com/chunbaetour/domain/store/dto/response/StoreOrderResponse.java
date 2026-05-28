package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.StoreOrder;
import com.chunbaetour.domain.store.type.StoreOrderStatus;
import java.time.LocalDateTime;

public record StoreOrderResponse(
        Long orderId,
        Long productId,
        String productName,
        long productPrice,
        int quantity,
        long totalPrice,
        StoreOrderStatus status,
        LocalDateTime createdAt
) {
    public static StoreOrderResponse from(StoreOrder order) {
        return new StoreOrderResponse(
                order.getId(),
                order.getProductId(),
                order.getProductName(),
                order.getProductPrice(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
