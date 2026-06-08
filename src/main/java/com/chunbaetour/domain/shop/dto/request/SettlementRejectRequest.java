package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettlementRejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
