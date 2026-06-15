package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.type.ProductCategory;

/**
 * 상품 카테고리 응답 (KAN-302).
 * code = enum 이름(프론트 로직/필터용), label = 한글 표시명(프론트 노출용).
 */
public record ProductCategoryResponse(String code, String label) {

    public static ProductCategoryResponse from(ProductCategory category) {
        // products.category는 NOT NULL 불변식이라 실제 null 진입은 없다.
        // 매핑 유틸의 방어적 가드로만 유지 — null 입력 시 NPE 대신 null DTO 반환 (KAN-302 리뷰).
        if (category == null) {
            return null;
        }
        return new ProductCategoryResponse(category.name(), category.getDisplayName());
    }
}
