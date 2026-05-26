package com.chunbaetour.domain.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 상인 신청 거절 시 거절 사유 (필수 입력) */
public record MerchantApplicationRejectRequest(
        @NotBlank @Size(max = 200) String rejectReason
) {
}
