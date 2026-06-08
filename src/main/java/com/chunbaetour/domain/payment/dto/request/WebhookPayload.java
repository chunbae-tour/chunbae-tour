package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebhookPayload(
    @NotBlank
    String type,
    @NotNull
    WebhookData data
) {
    public record WebhookData(
        @NotBlank
        String paymentId,
        @NotBlank
        String transactionId // PortOne V2 웹훅 스키마 기준 필드로 수정
    ) {
    }
}
