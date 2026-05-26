package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 메뉴 수정 요청 DTO (STORY-11).
 * null 필드는 수정하지 않음 (부분 수정 지원).
 */
public record MenuUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Min(1) @Max(9_999_999) Long price,
        @URL @Size(max = 500) String imageUrl,
        Boolean isAvailable
) {
}
