package com.chunbaetour.domain.payment.client;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class StubPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public PgOrderResult createOrder(String orderId, Long userId, Long amount) {
        return new PgOrderResult(
                "stub-pg-" + orderId,
                "https://stub-pg.chunbaetour.com/pay/" + orderId
        );
    }
}
