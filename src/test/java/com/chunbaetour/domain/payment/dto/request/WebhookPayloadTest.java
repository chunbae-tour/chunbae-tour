package com.chunbaetour.domain.payment.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("PortOne V2 웹훅의 transactionId를 파싱한다")
    void parse_portoneV2WebhookTransactionId() throws Exception {
        String rawBody = """
                {
                  "type": "Transaction.Paid",
                  "timestamp": "2024-04-25T10:00:00.000Z",
                  "data": {
                    "paymentId": "order-1",
                    "storeId": "store-id",
                    "transactionId": "tx-1"
                  }
                }
                """;

        WebhookPayload payload = objectMapper.readValue(rawBody, WebhookPayload.class);

        assertThat(payload.type()).isEqualTo("Transaction.Paid");
        assertThat(payload.data().paymentId()).isEqualTo("order-1");
        assertThat(payload.data().transactionId()).isEqualTo("tx-1");
    }
}
