package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.constraints.Size;

public record RefundRequest(
        @Size(max = 500) String reason
) {
}
