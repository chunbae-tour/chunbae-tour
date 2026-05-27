package com.chunbaetour.domain.store.dto.response;

public record ProductSummaryResponse(
        Long productId,
        String name,
        String category,
        long price,
        Long originalPrice,
        String imageUrl,
        String merchantName,
        int stock,
        int soldCount
) {}
