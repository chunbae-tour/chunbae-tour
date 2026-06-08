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
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        FestivalStatus status,
        FestivalProgressStatus progressStatus
) {
    public static SearchFestivalResponse from(com.chunbaetour.domain.festival.entity.Festival festival, FestivalProgressStatus progressStatus) {
        return new SearchFestivalResponse(
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

