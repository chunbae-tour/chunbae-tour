package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.math.BigDecimal;

/** 가게 조회/수정 응답 DTO (STORY-10). */
public record ShopResponse(
        Long id,
        Long userId,
        String shopName,
        String category,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String phone,
        String description,
        String imageUrls,
        String operatingHours,
        String closedDays,
        boolean isCertified,
        BigDecimal rating,
        int reviewCount,
        ShopStatus status
) {
    public static ShopResponse from(Shop shop) {
        return new ShopResponse(
                shop.getId(),
                shop.getUserId(),
                shop.getShopName(),
                shop.getCategory(),
                shop.getAddress(),
                shop.getLat(),
                shop.getLng(),
                shop.getPhone(),
                shop.getDescription(),
                shop.getImageUrls(),
                shop.getOperatingHours(),
                shop.getClosedDays(),
                shop.isCertified(),
                BigDecimal.valueOf(shop.getRating()),
                shop.getReviewCount(),
                shop.getStatus()
        );
    }
}
