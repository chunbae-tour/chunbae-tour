package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import java.time.LocalDate;

/**
 * 일별 캘린더 이벤트 항목 (KAN-97).
 */
public record DailyEventItem(
        Long festivalId,
        String name,
        String address,
        String relatedUrl,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        String type,
        FestivalProgressStatus progressStatus
) {
    public static DailyEventItem of(Festival f, LocalDate today) {
        return new DailyEventItem(
                f.getId(), f.getName(), f.getAddress(), f.getRelatedUrl(),
                f.getStartDate(), f.getEndDate(), f.getImageUrl(), "FESTIVAL",
                FestivalProgressStatus.of(f.getStartDate(), f.getEndDate(), today)
        );
    }

    public static DailyEventItem fromCache(FestivalCacheData d, LocalDate today) {
        return new DailyEventItem(
                d.id(), d.name(), d.address(), d.relatedUrl(),
                d.startDate(), d.endDate(), d.imageUrl(), "FESTIVAL",
                FestivalProgressStatus.of(d.startDate(), d.endDate(), today)
        );
    }
}
