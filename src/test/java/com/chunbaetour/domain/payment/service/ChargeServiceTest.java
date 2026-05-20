package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @InjectMocks
    private ChargeService chargeService;

    @Test
    @DisplayName("정상 충전 요청 시 orderUid를 반환한다")
    void charge_success_returns_orderUid() {
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        given(paymentOrderRepository.save(any(PaymentOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ChargeResponse response = chargeService.charge(1L, "idem-key-1", new ChargeRequest(10_000L, PaymentMethod.CARD));

        assertThat(response.orderUid()).isNotNull();
        verify(paymentOrderRepository).save(any(PaymentOrder.class));
        verify(paymentGatewayClient).preRegister(anyString(), anyLong());
        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("동일 멱등성 키로 재요청 시 PAY_007(중복 결제)을 던진다")
    void charge_duplicate_idempotency_throws_PAY_007() {
        willThrow(new PaymentException(ErrorCode.DUPLICATE_PAYMENT_REQUEST))
                .given(idempotencyService).checkAndMark("dup-key");

        assertThatThrownBy(() -> chargeService.charge(1L, "dup-key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
    }

    @Test
    @DisplayName("PG 사전등록 실패 시 멱등성 키를 해제하고 예외를 던진다")
    void charge_preRegister_failure_unmarks_idempotency_key() {
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .given(paymentGatewayClient).preRegister(anyString(), anyLong());

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService).unmark("key");
    }

    @Test
    @DisplayName("DB 저장 실패 시 멱등성 키를 해제하고 예외를 던진다")
    void charge_save_failure_unmarks_idempotency_key() {
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());
        willThrow(new RuntimeException("DB error"))
                .given(paymentOrderRepository).save(any(PaymentOrder.class));

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyService).unmark("key");
    }

    @Test
    @DisplayName("충전 금액이 5,000원 미만이면 PAY_002를 던진다")
    void charge_amount_too_low_throws_PAY_002() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(4_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_TOO_LOW);
    }

    @Test
    @DisplayName("충전 금액이 1,000원 단위가 아니면 PAY_003을 던진다")
    void charge_invalid_unit_throws_PAY_003() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(6_500L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHARGE_UNIT);
    }

    @Test
    @DisplayName("충전 금액이 100,000원 초과이면 PAY_004를 던진다")
    void charge_amount_exceeded_throws_PAY_004() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(200_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_EXCEEDED);
    }
}
