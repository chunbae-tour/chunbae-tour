package com.chunbaetour.domain.search.dto.response.integrated;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record IntegratedFestivalItem(
        Long id,
        String name,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        String address,
        String thumbnailUrl,
        String content
) implements IntegratedSearchItem {
    @Override
    public String getTargetType() {
        return "FESTIVAL";
    }
}
