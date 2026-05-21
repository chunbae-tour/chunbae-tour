package com.chunbaetour.domain.search.dto.response;

import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record SearchFestivalResponse(
        Long festivalId,
        String name,
        String description,
        String region,
        @JsonProperty("address") String location,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("imageUrl") String thumbnailUrl,
        FestivalStatus status,
        FestivalProgressStatus progressStatus
) {
    public static SearchFestivalResponse from(com.chunbaetour.domain.festival.entity.Festival festival, FestivalProgressStatus progressStatus) {
        return new SearchFestivalResponse(
                festival.getId(),
                festival.getName(),
                festival.getDescription(),
                festival.getRegion(),
                festival.getLocation(),
                festival.getStartDate(),
                festival.getEndDate(),
                festival.getThumbnailUrl(),
                festival.getStatus(),
                progressStatus
        );
    }
}

