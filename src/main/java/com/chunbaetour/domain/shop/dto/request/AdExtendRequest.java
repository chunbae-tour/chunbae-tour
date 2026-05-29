package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record AdExtendRequest(
        @Positive @Max(365) int extensionDays
) {
}
