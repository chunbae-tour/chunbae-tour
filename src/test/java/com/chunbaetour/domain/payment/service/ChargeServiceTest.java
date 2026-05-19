package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PgOrderResult;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
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
    void charge_success_returns_redirectUrl() {
        given(paymentGatewayClient.createOrder(anyString(), anyLong(), anyLong()))
                .willReturn(new PgOrderResult("pg-001", "https://stub-pg.chunbaetour.com/pay/test"));
        given(paymentOrderRepository.save(any(PaymentOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ChargeResponse response = chargeService.charge(1L, "idem-key-1", new ChargeRequest(10_000L));

        assertThat(response.redirectUrl()).contains("stub-pg.chunbaetour.com");
        assertThat(response.orderId()).isNotNull();
        verify(paymentOrderRepository).save(any(PaymentOrder.class));
    }

    @Test
    void charge_duplicate_idempotency_throws_PAY_007() {
        willThrow(new BusinessException(ErrorCode.DUPLICATE_PAYMENT_REQUEST))
                .given(idempotencyService).checkAndMark("dup-key");

        assertThatThrownBy(() -> chargeService.charge(1L, "dup-key", new ChargeRequest(10_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
    }

    @Test
    void charge_amount_too_low_throws_PAY_002() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(500L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_TOO_LOW);
    }

    @Test
    void charge_invalid_unit_throws_PAY_003() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(1_500L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHARGE_UNIT);
    }

    @Test
    void charge_amount_exceeded_throws_PAY_004() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(200_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_EXCEEDED);
    }
}
