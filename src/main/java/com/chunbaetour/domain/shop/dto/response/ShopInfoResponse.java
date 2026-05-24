package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import java.util.List;

/**
 * 가게 공개 정보 + 메뉴 목록 응답 — GET /api/v1/shops/{shopId}, 비인증 공개.
 *
 * <p>ShopResponse와 구분:
 * <ul>
 *   <li>{@link ShopResponse} — MERCHANT 인증 전용 (GET/PATCH /merchants/me/shop).
 *       본인 가게 관리용이라 userId, lat/lng, imageUrls, status 포함.</li>
 *   <li>{@link ShopInfoResponse} — 누구나 접근 가능한 공개 뷰.
 *       userId·lat/lng·imageUrls·status 제외, 메뉴 목록(menus) 추가.
 *       QR 스캔·앱 탐색 등 진입 경로 무관하게 사용.</li>
 * </ul>
 */
public record ShopInfoResponse(
        Long shopId,
        String shopName,
        String category,
        String address,
        String phone,
        String description,
        String operatingHours,
        String closedDays,
        double rating,
        int reviewCount,
        boolean isCertified,
        List<MenuResponse> menus
) {

    public static ShopInfoResponse from(Shop shop, List<Menu> menus) {
        return new ShopInfoResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getCategory(),
                shop.getAddress(),
                shop.getPhone(),
                shop.getDescription(),
                shop.getOperatingHours(),
                shop.getClosedDays(),
                shop.getRating(),
                shop.getReviewCount(),
                shop.isCertified(),
                menus.stream().map(MenuResponse::from).toList()
        );
    }
}
