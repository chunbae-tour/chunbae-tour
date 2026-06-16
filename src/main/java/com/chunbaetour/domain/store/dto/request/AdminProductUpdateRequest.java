package com.chunbaetour.domain.store.dto.request;

import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관리자 상품 수정 요청 DTO.
 * null 필드는 기존 값 유지 (부분 수정). 값 clear는 지원하지 않음.
 * status 직접 지정 가능 — ON_SALE·SOLD_OUT·HIDDEN 전환.
 */
public record AdminProductUpdateRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 2000) String description,
        ProductCategory category,
        @Min(1) Long price,
        @Min(0) Long originalPrice,
        @Min(0) Integer stock,
        @Size(max = 10) List<String> imageUrls,
        @Size(max = 100) String merchantName,
        @Positive Integer validityDays,
        @Min(1) Integer maxPerPerson,
        ProductStatus status
) {
    @AssertTrue(message = "originalPrice는 price 이상이어야 합니다")
    public boolean isOriginalPriceValid() {
        return originalPrice == null || price == null || originalPrice >= price;
    }
}
