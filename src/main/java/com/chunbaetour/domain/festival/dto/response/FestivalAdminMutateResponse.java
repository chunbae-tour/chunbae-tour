package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 축제 등록·수정 응답 (KAN-95).
 * create → updatedAt null / update → createdAt null 아닌 값도 포함되나 직렬화 시 표시.
 */
public record FestivalAdminMutateResponse(
        Long festivalId,
        String name,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        FestivalStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FestivalAdminMutateResponse of(Festival f) {
        return new FestivalAdminMutateResponse(
                f.getId(),
                f.getName(),
                f.getRegion(),
                f.getStartDate(),
                f.getEndDate(),
                f.getStatus(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
