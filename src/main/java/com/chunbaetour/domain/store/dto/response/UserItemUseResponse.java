package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDateTime;

public record UserItemUseResponse(
        Long itemId,
        Long productId,
        String productName,
        UserItemStatus status,
        LocalDateTime usedAt,
        Long usedShopId
) {
    // 내부 사용자 ID(userId)는 상인 응답에 노출하지 않는다 — 고객 식별/추적 악용 방지, 계약상 불필요.
    public static UserItemUseResponse from(UserItem item) {
        return new UserItemUseResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getStatus(),
                item.getUsedAt(),
                item.getUsedShopId()
        );
    }
}
