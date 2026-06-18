package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompanionPostCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotNull CompanionTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotBlank @Size(max = 100) String targetName,
        @Size(max = 50) String region,
        @NotNull @FutureOrPresent LocalDate meetingDate,
        @NotNull @Min(2) @Max(50) int maxMembers
) {
}
