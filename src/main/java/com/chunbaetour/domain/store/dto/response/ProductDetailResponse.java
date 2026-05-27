package com.chunbaetour.domain.store.dto.response;

import com.chunbaetour.domain.store.type.ProductStatus;
import java.util.List;

public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        String category,
        long price,
        Long originalPrice,
        List<String> imageUrls,
        String merchantName,
        int stock,
        int soldCount,
        Integer validityDays,
        ProductStatus status
) {}
