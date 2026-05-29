package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdRejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
