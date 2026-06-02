package com.chunbaetour.domain.admin.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 운영자 가게 단건 상세 (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>상인 본인용 {@code ShopResponse}와 달리 운영 판단에 필요한 인증 상태(isCertified)·리뷰 집계·상태·버전을
 * 모두 노출한다. 매출 그래프/신고 이력 상세는 Out of Scope(별도 admin 통계·신고 슬라이스) — 본 응답은
 * 가게 기본 + 인증 + 리뷰 집계 수준까지.
 *
 * <p>신고 누적: 현재 baseline {@code ReportTargetType}에 SHOP 항목이 없어 가게 단위 신고 카운트는 조회 불가 —
 * 본 슬라이스 범위에서 제외(신고 도메인이 SHOP 타겟을 지원하면 후속 보강).
 */
public record AdminShopDetailResponse(
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
        double rating,
        int reviewCount,
        ShopStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminShopDetailResponse from(Shop shop) {
        return new AdminShopDetailResponse(
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
                shop.getRating(),
                shop.getReviewCount(),
                shop.getStatus(),
                shop.getCreatedAt(),
                shop.getUpdatedAt());
    }
}
