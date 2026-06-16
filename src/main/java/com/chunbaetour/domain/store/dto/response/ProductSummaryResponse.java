package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.type.ProductStatus;

public record ProductSummaryResponse(
        Long productId,
        String name,
        ProductCategoryResponse category,
        long price,
        Long originalPrice,
        String imageUrl,
        String merchantName,
        int stock,
        int soldCount,
        ProductStatus status
) {}
