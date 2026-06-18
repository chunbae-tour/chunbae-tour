package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.BusinessStatus;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * 가게 공개 정보 + 메뉴 목록 응답 — GET /api/v1/shops/{shopId}, 비인증 공개.
 *
 * <p>ShopResponse와 구분:
 * <ul>
 *   <li>{@link ShopResponse} — MERCHANT 인증 전용 (GET/PATCH /merchants/me/shop).
 *       본인 가게 관리용이라 userId, lat/lng, imageUrls, status 포함.</li>
 *   <li>{@link ShopInfoResponse} — 누구나 접근 가능한 공개 뷰.
 *       userId·lat/lng 제외, status·메뉴 목록(menus) 추가.
 *       클라이언트가 SUSPENDED 등 비정상 상태를 "영업정지" 등으로 표시할 수 있도록 status 포함.
 *       QR 스캔·앱 탐색 등 진입 경로 무관하게 사용.</li>
 * </ul>
 *
 * <p>대표 이미지·갤러리(KAN-323): 상인 전용 {@code /merchants/me/shops/{id}/images}는 소유자 인증이 필요해
 * 일반/비로그인 사용자가 호출할 수 없으므로, 공개 뷰에서 대표사진·갤러리를 노출하기 위해 본 응답에 임베드한다.
 * {@code representativeImageUrl}은 PROFILE 1장(없으면 null), {@code images}는 GALLERY 목록이며 둘 다
 * presigned GET URL(만료 있음)로 변환된다.
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
        BigDecimal rating,
        int reviewCount,
        boolean isCertified,
        ShopStatus status,
        // 실시간 영업여부 — operatingHours + 현재시각(KST)으로 조회 시점 계산, 미저장 파생값 (B7, KAN-301)
        BusinessStatus businessStatus,
        List<MenuResponse> menus,
        // 대표 사진(PROFILE) presigned URL — 없으면 null (KAN-323)
        String representativeImageUrl,
        // 갤러리(GALLERY) 사진 목록 — 각 url은 presigned GET URL (KAN-323)
        List<ShopImageItemResponse> images
) {

    public static ShopInfoResponse from(Shop shop, List<Menu> menus, BusinessStatus businessStatus,
            String representativeImageUrl, List<ShopImageItemResponse> images) {
        return new ShopInfoResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getCategory(),
                shop.getAddress(),
                shop.getPhone(),
                shop.getDescription(),
                shop.getOperatingHours(),
                shop.getClosedDays(),
                BigDecimal.valueOf(shop.getRating()),
                shop.getReviewCount(),
                shop.isCertified(),
                shop.getStatus(),
                businessStatus,
                menus.stream().map(MenuResponse::from).toList(),
                representativeImageUrl,
                images
        );
    }
}
