package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.type.ProductCategory;
import java.util.Objects;

/**
 * 상품 카테고리 응답 (KAN-302).
 * code = enum 이름(프론트 로직/필터용), label = 한글 표시명(프론트 노출용).
 */
public record ProductCategoryResponse(String code, String label) {

    public static ProductCategoryResponse from(ProductCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return new ProductCategoryResponse(category.name(), category.getDisplayName());
    }
}
