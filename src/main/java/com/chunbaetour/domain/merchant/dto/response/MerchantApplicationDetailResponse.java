package com.chunbaetour.domain.merchant.dto.response;

import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.time.LocalDateTime;

public record MerchantApplicationDetailResponse(
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
    public static MerchantApplicationDetailResponse from(MerchantApplication application) {
        return new MerchantApplicationDetailResponse(
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
