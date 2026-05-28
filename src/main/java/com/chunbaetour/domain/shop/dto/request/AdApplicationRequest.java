package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AdApplicationRequest(
        @NotNull Long shopId,
        @NotBlank String adType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Positive @Max(10_000_000L) long cost
) {
}
