package com.chunbaetour.domain.community.companion.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompanionPostUpdateRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 5000) String content,
        Long placeId,
        @Size(min = 1, max = 100) String placeName,
        @Size(max = 50) String region,
        @FutureOrPresent LocalDate meetingDate,
        @Min(2) Integer maxMembers
) {
}
