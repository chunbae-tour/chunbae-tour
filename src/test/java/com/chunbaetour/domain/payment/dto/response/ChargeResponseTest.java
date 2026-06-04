package com.chunbaetour.domain.payment.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.payment.type.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChargeResponseTest {

    @Test
    @DisplayName("카드 결제수단은 PortOne CARD로 매핑한다")
    void from_cardPaymentMethod_mapsToCard() {
        ChargeResponse response = ChargeResponse.from(
                "order-1",
                10_000L,
                PaymentMethod.FOREIGN_CARD,
                "store-id",
                "channel-foreign-card"
        );

        assertThat(response.paymentId()).isEqualTo("order-1");
        assertThat(response.orderName()).isEqualTo("춘배투어 엽전 10000원 충전");
        assertThat(response.currency()).isEqualTo("CURRENCY_KRW");
        assertThat(response.payMethod()).isEqualTo("CARD");
    }

    @Test
    @DisplayName("간편결제 결제수단은 PortOne EASY_PAY로 매핑한다")
    void from_easyPaymentMethod_mapsToEasyPay() {
        ChargeResponse kakaoPayResponse = ChargeResponse.from(
                "order-1",
                10_000L,
                PaymentMethod.KAKAO_PAY,
                "store-id",
                "channel-kakao-pay"
        );
        ChargeResponse tossPayResponse = ChargeResponse.from(
                "order-2",
                10_000L,
                PaymentMethod.TOSS_PAY,
                "store-id",
                "channel-toss-pay"
        );

        assertThat(kakaoPayResponse.payMethod()).isEqualTo("EASY_PAY");
        assertThat(tossPayResponse.payMethod()).isEqualTo("EASY_PAY");
    }
}
