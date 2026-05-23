package com.chunbaetour.domain.merchant.dto.response;

import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.time.LocalDateTime;

/** 상인 신청 응답 DTO (신청 확인 및 관리자 목록 공용) */
public record MerchantApplicationResponse(
        Long applicationId,
        Long userId,
        String shopName,
        String businessNumber,
        String category,
        String address,
        MerchantApplicationStatus status,
        String rejectReason,
        LocalDateTime createdAt
) {
    public static MerchantApplicationResponse from(MerchantApplication application) {
        return new MerchantApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getShopName(),
                application.getBusinessNumber(),
                application.getCategory(),
                application.getAddress(),
                application.getStatus(),
                application.getRejectReason(),
                application.getCreatedAt()
        );
    }
}
