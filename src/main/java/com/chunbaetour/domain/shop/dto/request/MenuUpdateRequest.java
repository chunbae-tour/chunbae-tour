package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 수정 요청 DTO (STORY-11).
 * null 필드는 수정하지 않음 (부분 수정 지원).
 */
public record MenuUpdateRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 500) String description,
        @Min(1) Long price,
        @Size(max = 500) String imageUrl,
        Boolean isAvailable
) {
}
