package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import com.chunbaetour.domain.shop.type.AdType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdApplicationResponse(
        Long applicationId,
        Long shopId,
        AdType adType,
        LocalDate startDate,
        LocalDate endDate,
        long cost,
        AdApplicationStatus status,
        LocalDateTime createdAt
) {
    /**
     * 광고 신청 응답으로 변환한다.
     * 광고 연장 후에는 연장된 endDate를 포함한다.
     */
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
