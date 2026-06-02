package com.chunbaetour.domain.admin.certification.dto.response;

import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.time.LocalDateTime;

/**
 * 운영자 인증 신청 단건 상세 (KAN-204 Admin S05).
 *
 * <p>신청 사유 + 거절/취소 사유 + 처리 운영자/시각 등 전체 메타를 노출해 운영자가 기준 충족 여부를 판단하고,
 * 상인이 결과(승인/거절/취소/사유)를 확인할 수 있게 한다.
 *
 * <p>{@code isCertified}는 신청 이력(status)과 분리된 <b>가게의 현재 인증 여부</b>(shops.is_certified)를 노출한다 —
 * 리뷰 요청(승인 이력 vs 현재 인증여부 분리). 취소(CANCELLED) 시 status=CANCELLED + isCertified=false.
 */
public record ShopCertificationDetailResponse(
        Long id,
        Long shopId,
        ShopCertificationStatus status,
        String submittedReason,
        String rejectReason,
        String cancelReason,
        Long processedBy,
        LocalDateTime submittedAt,
        LocalDateTime processedAt,
        boolean isCertified
) {

    public static ShopCertificationDetailResponse from(ShopCertification cert, boolean isCertified) {
        return new ShopCertificationDetailResponse(
                cert.getId(),
                cert.getShopId(),
                cert.getStatus(),
                cert.getSubmittedReason(),
                cert.getRejectReason(),
                cert.getCancelReason(),
                cert.getProcessedBy(),
                cert.getSubmittedAt(),
                cert.getProcessedAt(),
                isCertified);
    }
}
