package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.PaymentHistoryResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PortOneProperties portOneProperties;

    @InjectMocks
    private ChargeService chargeService;

    @Test
    @DisplayName("정상 충전 요청 시 프론트 결제창 호출에 필요한 값을 반환한다")
    void charge_success_returns_payment_window_parameters() {
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        given(paymentOrderRepository.save(any(PaymentOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of(
                "card", "channel-card",
                "kakao-pay", "channel-kakao-pay",
                "toss-pay", "channel-toss-pay",
                "foreign-card", "channel-foreign-card"
        ));

        ChargeResponse response = chargeService.charge(1L, "idem-key-1", new ChargeRequest(10_000L, PaymentMethod.CARD));

        assertThat(response.orderUid()).isNotNull();
        assertThat(response.paymentId()).isEqualTo(response.orderUid());
        assertThat(response.storeId()).isEqualTo("store-id");
        assertThat(response.channelKey()).isEqualTo("channel-card");
        assertThat(response.orderName()).isEqualTo("춘배투어 엽전 10000원 충전");
        assertThat(response.totalAmount()).isEqualTo(10_000L);
        assertThat(response.currency()).isEqualTo("CURRENCY_KRW");
        assertThat(response.payMethod()).isEqualTo("CARD");
        verify(paymentOrderRepository).save(any(PaymentOrder.class));
        verify(paymentGatewayClient).preRegister(anyString(), anyLong());
        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("storeId 미설정(null) 시 멱등성 키 점유 전에 PAYMENT_SERVICE_UNAVAILABLE을 던진다")
    void charge_storeId_null_throws_before_idempotency_mark() {
        given(portOneProperties.getStoreId()).willReturn(null);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService, never()).checkAndMark(anyString());
    }

    @Test
    @DisplayName("채널키 미설정(맵 없음) 시 멱등성 키 점유 전에 PAYMENT_SERVICE_UNAVAILABLE을 던진다")
    void charge_channelKey_missing_throws_before_idempotency_mark() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(null);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService, never()).checkAndMark(anyString());
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

    // ── getPaymentHistory ────────────────────────────────────────────────────

    private PaymentOrder createOrder(Long id, Long amount) {
        PaymentOrder order = PaymentOrder.create(
                "order-" + id, 1L, amount, "idem-" + id, PaymentMethod.CARD, "pg-" + id);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("결제 내역 조회 — cursor 없으면 첫 페이지 반환")
    void getPaymentHistory_noCursor_returnsFirstPage() {
        PaymentOrder o1 = createOrder(10L, 10_000L);
        PaymentOrder o2 = createOrder(9L, 5_000L);
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(List.of(o1, o2));

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).orderUid()).isEqualTo("order-10");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("결제 내역 조회 — size+1개 조회 시 hasNext=true, nextCursor 설정")
    void getPaymentHistory_hasNextPage_nextCursorSet() {
        // size=2, DB에서 3개(size+1) 반환 → hasNext=true
        PaymentOrder o1 = createOrder(10L, 10_000L);
        PaymentOrder o2 = createOrder(9L, 5_000L);
        PaymentOrder o3 = createOrder(8L, 3_000L);
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(List.of(o1, o2, o3));

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("결제 내역 조회 — 결과 없으면 빈 목록")
    void getPaymentHistory_noResults_returnsEmpty() {
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }
}
