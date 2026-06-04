package com.chunbaetour.domain.search.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;

public record SearchFestivalV1Response(
        Long festivalId,
        String name,
        String description,
        String region,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        FestivalStatus status,
        FestivalProgressStatus progressStatus
) {
    public static SearchFestivalV1Response from(Festival festival, FestivalProgressStatus progressStatus) {
        return new SearchFestivalV1Response(
                festival.getId(),
                festival.getName(),
                festival.getDescription(),
                festival.getRegion(),
                festival.getAddress(),
                festival.getStartDate(),
                festival.getEndDate(),
                festival.getImageUrl(),
                festival.getStatus(),
                progressStatus
        );
    }
}
