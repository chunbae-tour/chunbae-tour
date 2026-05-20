package com.chunbaetour.domain.payment.dto.request;

import com.chunbaetour.domain.payment.type.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChargeRequest(
    @NotNull(message = "결제 금액은 필수 입력값입니다.")
    @Min(value = 5000, message = "충전은 5,000원부터 가능합니다.")
    @Max(value = 100000, message = "충전은 100,000원까지 가능합니다.")
    Long amount,

    @NotNull(message = "결제수단은 필수 입력사항입니다.")
    PaymentMethod paymentMethod) {
}
