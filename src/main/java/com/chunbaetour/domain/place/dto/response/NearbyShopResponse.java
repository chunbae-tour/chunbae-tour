package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record NearbyShopResponse(
        Long shopId,
        String shopName,
        String category,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        double distanceMeters,
        ShopStatus status,
        double rating,
        int reviewCount,
        String imageUrls
) {
    public static NearbyShopResponse fromWithDistance(Shop shop, double distanceMeters) {
        return NearbyShopResponse.builder()
                .shopId(shop.getId())
                .shopName(shop.getShopName())
                .category(shop.getCategory())
                .address(shop.getAddress())
                .lat(shop.getLat())
                .lng(shop.getLng())
                .distanceMeters(distanceMeters)
                .status(shop.getStatus())
                .rating(shop.getRating())
                .reviewCount(shop.getReviewCount())
                .imageUrls(shop.getImageUrls())
                .build();
    }
}
