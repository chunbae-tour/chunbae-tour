package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;

/**
 * 축제 응답 DTO — 사용자 목록·상세·관리자 목록 공용 (KAN-95/97/98).
 * progressStatus는 today 기준 동적 계산.
 */
public record FestivalResponse(
        Long festivalId,
        String name,
        String description,
        String region,
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        String relatedUrl,
        FestivalStatus status,
        FestivalProgressStatus progressStatus
) {
    public static FestivalResponse of(Festival f) {
        return new FestivalResponse(
                f.getId(),
                f.getName(),
                f.getDescription(),
                f.getRegion(),
                f.getAddress(),
                f.getStartDate(),
                f.getEndDate(),
                f.getImageUrl(),
                f.getRelatedUrl(),
                f.getStatus(),
                FestivalProgressStatus.of(f.getStartDate(), f.getEndDate(), LocalDate.now())
        );
    }
}
