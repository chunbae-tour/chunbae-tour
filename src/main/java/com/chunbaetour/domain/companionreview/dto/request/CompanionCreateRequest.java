package com.chunbaetour.domain.companionreview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CompanionCreateRequest(
        @NotEmpty List<@NotNull Long> participantUserIds,
        @Schema(description = "동행 여행 시작일") @NotNull LocalDate tripStartDate,
        @Schema(description = "동행 여행 종료일 (tripStartDate 이상)") @NotNull LocalDate tripEndDate
) {}
