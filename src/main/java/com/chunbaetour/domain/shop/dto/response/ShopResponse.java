package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.math.BigDecimal;

/** 가게 조회/수정 응답 DTO (STORY-10). */
public record ShopResponse(
        Long id,
        Long userId,
        Long placeId,
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
        return from(shop, shop.getImageUrls());
    }

    /**
     * imageUrls를 외부에서 주입하는 오버로드 (E10 PR3).
     * 저장값은 S3 객체 키 JSON 배열이지만, 응답에는 ShopService가 presigned GET URL JSON 배열로 변환해 내려준다.
     */
    public static ShopResponse from(Shop shop, String imageUrls) {
        return new ShopResponse(
                shop.getId(),
                shop.getUserId(),
                shop.getPlaceId(),
                shop.getShopName(),
                shop.getCategory(),
                shop.getAddress(),
                shop.getLat(),
                shop.getLng(),
                shop.getPhone(),
                shop.getDescription(),
                imageUrls,
                shop.getOperatingHours(),
                shop.getClosedDays(),
                shop.isCertified(),
                BigDecimal.valueOf(shop.getRating()),
                shop.getReviewCount(),
                shop.getStatus()
        );
    }
}
