package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdApplicationResponse(
        Long applicationId,
        Long shopId,
        String adType,
        LocalDate startDate,
        LocalDate endDate,
        long cost,
        AdApplicationStatus status,
        LocalDateTime createdAt
) {
    public static AdApplicationResponse from(AdApplication a) {
        return new AdApplicationResponse(
                a.getId(),
                a.getShopId(),
                a.getAdType(),
                a.getStartDate(),
                a.getEndDate(),
                a.getCost(),
                a.getStatus(),
                a.getCreatedAt()
        );
    }
}
