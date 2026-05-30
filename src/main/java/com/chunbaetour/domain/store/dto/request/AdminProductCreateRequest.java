package com.chunbaetour.domain.store.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminProductCreateRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank @Size(max = 50) String category,
        @NotNull @Min(1) Long price,
        Long originalPrice,
        @NotNull @Min(0) Integer stock,
        String imageUrls,
        @Size(max = 100) String merchantName,
        Integer validityDays,
        @NotNull @Min(1) Integer maxPerPerson
) {
}
