package com.chunbaetour.domain.admin.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.time.LocalDateTime;

/**
 * 운영자 가게 목록 항목 응답 (KAN-307).
 *
 * <p>목록은 식별/검색에 필요한 요약 필드 + 연결 장소/시장(id·name)만 노출한다(상세는 {@link AdminShopDetailResponse}).
 * 이름은 별도 테이블(Place/TraditionalMarket)이라 서비스가 페이지 단위 배치 조회로 채워 전달한다(N+1 회피).
 */
public record AdminShopListResponse(
        Long id,
        Long userId,
        String shopName,
        String category,
        String address,
        String phone,
        boolean isCertified,
        ShopStatus status,
        Long placeId,
        String placeName,
        Long traditionalMarketId,
        String traditionalMarketName,
        LocalDateTime createdAt
) {

    /**
     * @param placeName  연결된 Place 이름(미연결·미존재 시 null)
     * @param marketName 연결된 TraditionalMarket 이름(미연결·미존재 시 null)
     */
    public static AdminShopListResponse from(Shop shop, String placeName, String marketName) {
        return new AdminShopListResponse(
                shop.getId(),
                shop.getUserId(),
                shop.getShopName(),
                shop.getCategory(),
                shop.getAddress(),
                shop.getPhone(),
                shop.isCertified(),
                shop.getStatus(),
                shop.getPlaceId(),
                placeName,
                shop.getTraditionalMarketId(),
                marketName,
                shop.getCreatedAt());
    }
}
