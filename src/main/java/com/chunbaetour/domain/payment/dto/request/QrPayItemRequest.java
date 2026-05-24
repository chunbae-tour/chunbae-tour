package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QrPayItemRequest(
        @NotNull Long menuId,
        @Min(1) @Max(999) int quantity
) {}
