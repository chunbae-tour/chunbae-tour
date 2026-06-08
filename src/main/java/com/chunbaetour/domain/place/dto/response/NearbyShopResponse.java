package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Builder
public record NearbyShopResponse(
        Long shopId,
        String shopName,
        String category,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        double distanceMeters,
        double rating,
        int reviewCount,
        List<String> imageUrls
) {
    private static final Logger log = LoggerFactory.getLogger(NearbyShopResponse.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static NearbyShopResponse fromWithDistance(Shop shop, double distanceMeters) {
        List<String> parsedImages = List.of();
        if (shop.getImageUrls() != null && !shop.getImageUrls().isBlank()) {
            try {
                parsedImages = MAPPER.readValue(shop.getImageUrls(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("상점 이미지 URL 파싱 실패. shopId={}", shop.getId(), e);
            }
        }
        return NearbyShopResponse.builder()
                .shopId(shop.getId())
                .shopName(shop.getShopName())
                .category(shop.getCategory())
                .address(shop.getAddress())
                .lat(shop.getLat())
                .lng(shop.getLng())
                .distanceMeters(distanceMeters)
                .rating(shop.getRating())
                .reviewCount(shop.getReviewCount())
                .imageUrls(parsedImages)
                .build();
    }
}
