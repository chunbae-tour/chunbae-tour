package com.chunbaetour.domain.store.dto.request;

import com.chunbaetour.domain.store.type.ProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 관리자 상품 수정 요청 DTO.
 * null 필드는 기존 값 유지 (부분 수정).
 * status 직접 지정 가능 — ON_SALE·SOLD_OUT·HIDDEN 전환.
 */
public record AdminProductUpdateRequest(
        @Size(min = 1, max = 100) String name,
        String description,
        @Size(min = 1, max = 50) String category,
        @Min(1) Long price,
        @Min(0) Long originalPrice,
        @Min(0) Integer stock,
        String imageUrls,
        @Size(max = 100) String merchantName,
        Integer validityDays,
        @Min(1) Integer maxPerPerson,
        ProductStatus status
) {
}
