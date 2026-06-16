package com.chunbaetour.domain.payment.client;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 포트원 V2 REST API를 호출하는 실제 PG 클라이언트.
 * <p>
 * 결제 사전등록(pre-register): 프론트엔드가 포트원 SDK로 결제창을 띄우기 전,
 * 서버가 포트원에 금액을 미리 등록해둔다. 이렇게 하면 사용자가 결제 금액을
 * 임의로 조작하더라도 포트원이 서버에 등록된 금액으로 검증해 위변조를 막는다.
 * <p>
 * 실패 시 PAY_005(결제 서비스 이용 불가) 예외를 던진다.
 */
@Primary
@Component
@RequiredArgsConstructor
public class PortOnePaymentGatewayClient implements PaymentGatewayClient {

    private final RestClient portOneRestClient;
    private final PortOneProperties properties;

    @Override
    public void preRegister(String orderUid, Long amount) {
        try {
            portOneRestClient.post()
                .uri("/payments/{paymentId}/pre-register", orderUid)
                .header("Authorization", "PortOne " + properties.getSecret())
                .body(new PreRegisterRequest(properties.getStoreId(), amount, "KRW"))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public PortOnePaymentInfo verifyPayment(String paymentId) {
        try {
            PortOnePaymentResponse response = portOneRestClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .header("Authorization", "PortOne " + properties.getSecret())
                .retrieve()
                .body(PortOnePaymentResponse.class);
            if (response == null
                || response.status() == null
                || response.amount() == null
                || response.amount().total() == null) {
                throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
            }
            // pgTxId: 고아 PENDING 재조정(B3)이 PAID 복구 시 pgTransactionId로 저장할 PG 거래번호.
            // 웹훅 경로는 payload의 transactionId를 쓰지만, GET 조회 경로엔 webhook이 없어 응답의 pgTxId로 대체한다.
            return new PortOnePaymentInfo(
                response.status(), response.amount().total(), response.amount().cancelled(), response.pgTxId());
        } catch (RestClientException e) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void cancelPayment(String orderUid, Long amount, String reason, String idempotencyKey) {
        try {
            portOneRestClient.post()
                .uri("/payments/{paymentId}/cancel", orderUid)
                .header("Authorization", "PortOne " + properties.getSecret())
                // refundId 기반 멱등키: 동일 환불 건 재시도 시 PortOne이 이미 처리한 요청을 중복 없이 반환
                .header("Idempotency-Key", idempotencyKey)
                .body(new CancelRequest(properties.getStoreId(), reason, amount))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
    }

    private record PreRegisterRequest(String storeId, Long totalAmount, String currency) {
    }

    private record CancelRequest(String storeId, String reason, Long amount) {
    }

    private record PortOnePaymentResponse(String status, String pgTxId, AmountDetail amount) {
        record AmountDetail(Long total, Long cancelled) {
        }
    }
}
