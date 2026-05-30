package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 정산 계좌 등록/변경 요청 DTO.
 * ShopWallet의 bankName, accountNumber, accountHolder를 전체 교체(PUT).
 */
public record ShopAccountRequest(
        @NotBlank @Size(max = 50) String bankName,
        @NotBlank @Pattern(regexp = "^[\\d\\-]+$", message = "계좌번호는 숫자와 하이픈만 허용합니다")
        @Size(min = 10, max = 30) String accountNumber,
        @NotBlank @Size(max = 50) String accountHolder
) {
}
