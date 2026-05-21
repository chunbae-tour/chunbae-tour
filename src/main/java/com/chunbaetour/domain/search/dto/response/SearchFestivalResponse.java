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
}

