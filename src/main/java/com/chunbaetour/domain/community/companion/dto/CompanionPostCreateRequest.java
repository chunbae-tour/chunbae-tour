package com.chunbaetour.domain.community.companion.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompanionPostCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotNull Long placeId,
        @NotBlank @Size(max = 100) String placeName,
        @Size(max = 50) String region,
        @NotNull @Future LocalDate meetingDate,
        @NotNull @Min(2) int maxMembers
) {
}
