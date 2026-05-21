package com.chunbaetour.domain.merchant.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 상인 등록 신청 요청 DTO */
public record MerchantApplyRequest(
        @NotBlank @Size(max = 50) String shopName,
        @NotBlank @Pattern(regexp = "^\\d{10}$|^\\d{3}-\\d{2}-\\d{5}$") String businessNumber,
        @NotBlank @Size(max = 50) String category,
        @NotBlank String address,
        @NotNull @DecimalMin("-90.0000000") @DecimalMax("90.0000000") BigDecimal lat,
        @NotNull @DecimalMin("-180.0000000") @DecimalMax("180.0000000") BigDecimal lng,
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$") String phone,
        @Size(max = 500) String description
) {
}
