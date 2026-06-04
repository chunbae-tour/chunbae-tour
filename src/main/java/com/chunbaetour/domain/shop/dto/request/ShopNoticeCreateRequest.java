package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopNoticeCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank String content
) {
}
