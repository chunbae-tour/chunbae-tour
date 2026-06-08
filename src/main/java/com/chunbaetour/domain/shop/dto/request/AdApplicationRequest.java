package com.chunbaetour.domain.shop.dto.request;

import com.chunbaetour.domain.shop.type.AdType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AdApplicationRequest(
        @NotNull Long shopId,
        @NotNull AdType adType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
