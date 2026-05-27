package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QrPayItemRequest(
        @NotNull @Positive Long menuId,
        @NotNull @Min(1) @Max(999) Integer quantity
) {}
