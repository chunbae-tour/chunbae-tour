package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AdApplicationRequest(
        @NotNull Long shopId,
        @NotBlank String adType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Positive long cost
) {
}
