package com.chunbaetour.domain.community.companion.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompanionPostUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String content,
        Long placeId,
        @Size(max = 100) String placeName,
        @Size(max = 50) String region,
        @Future LocalDate meetingDate,
        @Min(2) Integer maxMembers
) {
}
