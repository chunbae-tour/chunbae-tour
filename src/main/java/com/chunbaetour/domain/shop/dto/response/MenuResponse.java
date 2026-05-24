package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MenuResponse(
        Long id,
        Long shopId,
        String name,
        String description,
        Long price,
        String imageUrl,
        // Record boolean 필드는 Jackson이 isXxx() accessor의 is 접두사를 제거해 "available"로 직렬화 — 명시적으로 키 고정
        @JsonProperty("isAvailable") boolean isAvailable
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
