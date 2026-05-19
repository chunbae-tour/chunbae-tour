package com.chunbaetour.domain.payment.client;

public interface PaymentGatewayClient {

    PgOrderResult createOrder(String orderId, Long userId, Long amount);

    record PgOrderResult(String pgOrderId, String redirectUrl) {}
}
