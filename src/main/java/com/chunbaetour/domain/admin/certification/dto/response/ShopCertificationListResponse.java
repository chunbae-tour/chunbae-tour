package com.chunbaetour.domain.admin.certification.dto.response;

import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.time.LocalDateTime;

/**
 * 운영자 인증 신청 목록 항목 (KAN-204 Admin S05).
 *
 * <p>목록은 식별/상태/신청시각 위주 최소 필드만 노출. 신청 사유/거절 사유 등 상세 정보는 단건 상세
 * ({@link ShopCertificationDetailResponse})에서 제공.
 */
public record ShopCertificationListResponse(
        Long id,
        Long shopId,
        ShopCertificationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime processedAt
) {

    public static ShopCertificationListResponse from(ShopCertification cert) {
        return new ShopCertificationListResponse(
                cert.getId(),
                cert.getShopId(),
                cert.getStatus(),
                cert.getSubmittedAt(),
                cert.getProcessedAt());
    }
}
