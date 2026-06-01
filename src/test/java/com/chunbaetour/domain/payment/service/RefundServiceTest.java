package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_UID = "test-order-uid";
    private static final Long AMOUNT = 10_000L;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private WalletService walletService;

    @Mock
    private Clock clock;

    @InjectMocks
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        Clock systemClock = Clock.systemUTC();
        lenient().when(clock.instant()).thenReturn(systemClock.instant());
        lenient().when(clock.getZone()).thenReturn(systemClock.getZone());
    }

    @Test
    @DisplayName("unused charge refund calls PortOne cancel and completes refund")
    void requestRefund_success_refunds_immediately() {
        PaymentOrder order = completedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(100L)).willReturn(Optional.empty());
        given(refundRepository.saveAndFlush(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        verify(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");
        verify(walletService).reclaimForRefund(USER_ID, AMOUNT, 100L);
        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("partially used charge refund is rejected")
    void requestRefund_partialUse_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet(AMOUNT - 1)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_BALANCE_INSUFFICIENT);

        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("charge with later spend history is rejected even when wallet still has enough balance")
    void requestRefund_laterSpendHistory_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        YeopjeonHistory chargeHistory = mock(YeopjeonHistory.class);
        given(chargeHistory.getId()).willReturn(10L);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(yeopjeonHistoryRepository.findFirstByPaymentOrderIdAndTypeOrderByIdAsc(100L, YeopjeonHistoryType.CHARGE))
                .willReturn(Optional.of(chargeHistory));
        given(yeopjeonHistoryRepository.existsByUserIdAndIdGreaterThanAndTypeIn(
                USER_ID,
                10L,
                List.of(YeopjeonHistoryType.PAYMENT)
        )).willReturn(true);

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_BALANCE_INSUFFICIENT);

        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("PortOne cancel failure keeps order unrefunded and records failed refund")
    void requestRefund_portOneFailure_doesNotMarkRefunded() {
        PaymentOrder order = completedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(100L)).willReturn(Optional.empty());
        given(refundRepository.saveAndFlush(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));
        doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        verify(walletService, never()).reclaimForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("duplicate refund request for already refunded order is idempotent")
    void requestRefund_alreadyRefunded_returnsExistingRefund() {
        PaymentOrder order = completedOrder(USER_ID);
        order.refund();
        Refund refund = Refund.create(100L, USER_ID, AMOUNT, "user request");
        refund.approve();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(100L)).willReturn(Optional.of(refund));

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("non-owner refund request is forbidden")
    void requestRefund_otherUser_throws() {
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(completedOrder(999L)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("refund history supports status filter")
    void getUserRefundHistory_statusFilter() {
        Refund refund = mock(Refund.class);
        given(refund.getId()).willReturn(10L);
        given(refund.getPaymentOrderId()).willReturn(100L);
        given(refund.getAmount()).willReturn(AMOUNT);
        given(refund.getStatus()).willReturn(RefundStatus.PENDING);
        given(refund.getReason()).willReturn("reason");
        given(refund.getCreatedAt()).willReturn(LocalDateTime.of(2026, 5, 25, 10, 0, 0));
        given(refundRepository.findByUserIdWithFilter(eq(USER_ID), eq(RefundStatus.PENDING), eq(null), any(PageRequest.class)))
                .willReturn(List.of(refund));

        CursorPageResponse<UserRefundResponse> result =
                refundService.getUserRefundHistory(USER_ID, RefundStatus.PENDING, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    @DisplayName("invalid refund history cursor throws")
    void getUserRefundHistory_invalidCursor_throws() {
        assertThatThrownBy(() -> refundService.getUserRefundHistory(USER_ID, null, "not-valid-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("pending refund can be cancelled by owner")
    void cancelRefund_success() {
        Refund refund = Refund.create(100L, USER_ID, AMOUNT, "reason");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        refundService.cancelRefund(USER_ID, 1L);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CANCELLED);
    }

    private Wallet wallet(long balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private PaymentOrder completedOrder(Long userId) {
        PaymentOrder order = PaymentOrder.create(ORDER_UID, userId, AMOUNT, "idem-key", PaymentMethod.CARD, "pg-order-id");
        ReflectionTestUtils.setField(order, "id", 100L);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        return order;
    }
}
