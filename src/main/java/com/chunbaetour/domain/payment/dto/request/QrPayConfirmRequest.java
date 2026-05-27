package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * QR 결제 승인/거절 요청 DTO.
 * action=REJECT 시 rejectReason 선택 입력 (null 허용).
 */
public record QrPayConfirmRequest(

        @NotNull
        Action action,

        @Size(max = 200)
        String rejectReason

) {
    public enum Action {
        APPROVE, REJECT
    }
}
