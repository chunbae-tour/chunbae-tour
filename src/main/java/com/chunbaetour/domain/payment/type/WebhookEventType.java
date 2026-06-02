package com.chunbaetour.domain.payment.type;

import java.util.Arrays;

public enum WebhookEventType {
    TRANSACTION_PAID("Transaction.Paid"),
    TRANSACTION_FAILED("Transaction.Failed"),
    TRANSACTION_CANCELLED("Transaction.Cancelled"),
    TRANSACTION_PARTIAL_CANCELLED("Transaction.PartialCancelled"),
    TRANSACTION_CANCEL_PENDING("Transaction.CancelPending"),
    UNKNOWN("");

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    public static WebhookEventType from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
