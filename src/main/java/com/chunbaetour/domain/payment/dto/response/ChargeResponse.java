package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.type.PaymentMethod;

public record ChargeResponse(
    // 내부 주문 식별자. PaymentOrder.orderUid와 동일하다.
    String orderUid,
    // PortOne requestPayment의 paymentId. 사전등록한 orderUid와 동일하게 사용한다.
    // 동일값으로 내려가는 이유: PortOne V2 SDK가 paymentId를 결제 식별자로 사용하고, 서버가 사전등록한 orderUid와 일치해야 위변조 검증이 가능.
    // 두 필드를 분리 유지하는 이유: 향후 paymentId가 orderUid와 달라지는 정책 변경에 대비.
    String paymentId,
    // PortOne 상점 ID. portone.store-id 설정값이다.
    String storeId,
    // 결제수단별 PortOne 채널키. 카드/카카오페이/토스페이/해외카드별로 다르다.
    String channelKey,
    // 결제창에 표시될 주문명.
    String orderName,
    // PortOne requestPayment의 결제 총액.
    Long totalAmount,
    // PortOne V2 SDK 통화 코드.
    String currency,
    // PortOne V2 SDK 결제수단 코드.
    String payMethod
) {
    private static final String CURRENCY = "CURRENCY_KRW";

    public static ChargeResponse from(
        String orderUid,
        Long amount,
        PaymentMethod paymentMethod,
        String storeId,
        String channelKey
    ) {
        return new ChargeResponse(
            orderUid,
            orderUid,
            storeId,
            channelKey,
            createOrderName(amount),
            amount,
            CURRENCY,
            toPortOnePayMethod(paymentMethod)
        );
    }

    // 결제창에 표시될 주문명 생성 — PortOne SDK의 orderName 필드에 그대로 노출됨
    private static String createOrderName(Long amount) {
        return "춘배투어 엽전 " + amount + "원 충전";
    }

    // PortOne V2 SDK가 요구하는 payMethod 코드로 변환
    // CARD/FOREIGN_CARD → "CARD", KAKAO_PAY/TOSS_PAY → "EASY_PAY"
    private static String toPortOnePayMethod(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case CARD, FOREIGN_CARD -> "CARD";
            case KAKAO_PAY, TOSS_PAY -> "EASY_PAY";
        };
    }
}
