package com.chunbaetour.domain.admin.certification.dto.response;

import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.time.LocalDateTime;

/**
 * 운영자 인증 신청 단건 상세 (KAN-204 Admin S05).
 *
 * <p>신청 사유 + 거절 사유 + 처리 운영자/시각 등 전체 메타를 노출해 운영자가 기준 충족 여부를 판단하고,
 * 상인이 결과(승인/거절/사유)를 확인할 수 있게 한다.
 */
public record ShopCertificationDetailResponse(
        Long id,
        Long shopId,
        ShopCertificationStatus status,
        String submittedReason,
        String rejectReason,
        Long processedBy,
        LocalDateTime submittedAt,
        LocalDateTime processedAt
) {

    public static ShopCertificationDetailResponse from(ShopCertification cert) {
        return new ShopCertificationDetailResponse(
                cert.getId(),
                cert.getShopId(),
                cert.getStatus(),
                cert.getSubmittedReason(),
                cert.getRejectReason(),
                cert.getProcessedBy(),
                cert.getSubmittedAt(),
                cert.getProcessedAt());
    }
}
