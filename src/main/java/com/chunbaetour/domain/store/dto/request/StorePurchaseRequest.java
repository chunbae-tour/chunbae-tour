package com.chunbaetour.domain.store.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StorePurchaseRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(99) Integer quantity
) {}
