package com.chunbaetour.domain.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UserItemUseRequest(
        @NotNull @Positive Long shopId,
        @NotBlank String token
) {
}
