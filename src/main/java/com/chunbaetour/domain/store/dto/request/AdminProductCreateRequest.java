package com.chunbaetour.domain.store.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminProductCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 50) String category,
        @NotNull @Min(1) Long price,
        @Min(0) Long originalPrice,
        @NotNull @Min(0) Integer stock,
        @Size(max = 10) List<String> imageUrls,
        @Size(max = 100) String merchantName,
        Integer validityDays,
        @NotNull @Min(1) Integer maxPerPerson
) {
    @AssertTrue(message = "originalPrice는 price 이상이어야 합니다")
    public boolean isOriginalPriceValid() {
        return originalPrice == null || price == null || originalPrice >= price;
    }
}
