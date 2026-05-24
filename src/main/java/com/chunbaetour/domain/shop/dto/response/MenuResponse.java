package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;

public record MenuResponse(
        Long id,
        Long shopId,
        String name,
        String description,
        Long price,
        String imageUrl,
        boolean isAvailable
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getShopId(),
                menu.getName(),
                menu.getDescription(),
                menu.getPrice(),
                menu.getImageUrl(),
                menu.isAvailable()
        );
    }
}
