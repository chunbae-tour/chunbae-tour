package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import com.chunbaetour.domain.shop.type.AdType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminAdApplicationResponse(
        Long applicationId,
        Long shopId,
        AdType adType,
        LocalDate startDate,
        LocalDate endDate,
        long cost,
        AdApplicationStatus status,
        String rejectReason,
        LocalDateTime createdAt
) {
    public static AdminAdApplicationResponse from(AdApplication a) {
        return new AdminAdApplicationResponse(
                a.getId(),
                a.getShopId(),
                a.getAdType(),
                a.getStartDate(),
                a.getEndDate(),
                a.getCost(),
                a.getStatus(),
                a.getRejectReason(),
                a.getCreatedAt()
        );
    }
}
