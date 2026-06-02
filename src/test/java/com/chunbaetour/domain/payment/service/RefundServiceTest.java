package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
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
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_UID = "test-order-uid";
    private static final Long AMOUNT = 10_000L;
    private static final Long ORDER_ID = 100L;
    private static final Long REFUND_ID = 200L;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private WalletService walletService;

    @Mock
    private Clock clock;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        Clock systemClock = Clock.systemUTC();
        lenient().when(clock.instant()).thenReturn(systemClock.instant());
        lenient().when(clock.getZone()).thenReturn(systemClock.getZone());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                        .doInTransaction(null));
        lenient().doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<?>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("미사용 충전분 환불은 PortOne 취소 후 주문/환불/엽전 회수를 완료한다")
    void requestRefund_success_refundsImmediately() {
        PaymentOrder order = completedOrder(USER_ID);
        stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(AMOUNT);

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        verify(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");
        verify(walletService).reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID);
        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("잔액이 부족하면 PortOne 취소 호출 전에 환불을 거절한다")
    void requestRefund_partialUse_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT - 1)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_BALANCE_INSUFFICIENT);

        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("충전 이후 다른 사용 이력이 있어도 잔액이 충분하면 환불을 허용한다")
    void requestRefund_laterSpendHistoryDoesNotBlockWhenBalanceEnough() {
        PaymentOrder order = completedOrder(USER_ID);
        stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(AMOUNT);

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("PortOne 취소 실패 시 주문은 환불 완료로 바꾸지 않고 환불 이력만 FAILED 처리한다")
    void requestRefund_portOneFailure_doesNotMarkRefunded() {
        PaymentOrder order = completedOrder(USER_ID);
        AtomicReference<Refund> refundRef = stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(refundRef.get().getStatus()).isEqualTo(RefundStatus.FAILED);
        verify(walletService, never()).reclaimAvailableForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("PortOne 취소 중 예상치 못한 예외가 발생해도 환불 이력만 FAILED 처리한다")
    void requestRefund_portOneUnexpectedRuntimeException_marksRefundFailed() {
        PaymentOrder order = completedOrder(USER_ID);
        AtomicReference<Refund> refundRef = stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        doThrow(new IllegalStateException("unexpected gateway failure"))
                .when(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(refundRef.get().getStatus()).isEqualTo(RefundStatus.FAILED);
        verify(walletService, never()).reclaimAvailableForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("[KAN-205] cancelPayment 실패 후 PG 상태가 CANCELLED → 환불 완료 처리 (타임아웃 후 재시도 정산 불일치 방지)")
    void requestRefund_cancelPaymentFails_butPGAlreadyCancelled_completesRefund() {
        // 시나리오: cancelPayment 호출 후 네트워크 타임아웃 → 서버는 예외 수신, PG는 취소 완료.
        // verifyPayment로 CANCELLED 확인 → completeRefundAfterGatewayCancel로 진행해 정산 불일치 방지.
        PaymentOrder order = completedOrder(USER_ID);
        AtomicReference<Refund> refundRef = stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("CANCELLED", AMOUNT, AMOUNT));
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(AMOUNT);

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(refundRef.get().getStatus()).isEqualTo(RefundStatus.APPROVED);
        verify(walletService).reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID);
    }

    @Test
    @DisplayName("[KAN-205] cancelPayment 실패 + verifyPayment도 실패 → FAILED 처리 (PG 상태 불명 시 보수적 처리)")
    void requestRefund_cancelPaymentFails_verifyAlsoFails_marksRefundFailed() {
        PaymentOrder order = completedOrder(USER_ID);
        AtomicReference<Refund> refundRef = stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        doThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .when(paymentGatewayClient).cancelPayment(ORDER_UID, AMOUNT, "user request");
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(refundRef.get().getStatus()).isEqualTo(RefundStatus.FAILED);
        verify(walletService, never()).reclaimAvailableForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("PortOne 취소 성공 후 회수 가능 잔액이 부족하면 회수필요 상태로 남긴다")
    void requestRefund_gatewaySuccessButWalletShort_marksAdjustmentRequired() {
        PaymentOrder order = completedOrder(USER_ID);
        stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(3_000L);

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.ADJUSTMENT_REQUIRED);
    }

    @Test
    @DisplayName("이미 환불 완료된 주문은 기존 APPROVED 환불을 멱등 반환한다")
    void requestRefund_alreadyRefunded_returnsExistingRefund() {
        PaymentOrder order = completedOrder(USER_ID);
        order.refund();
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "user request");
        refund.approve();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.of(refund));

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
        verify(paymentGatewayClient, never()).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 주문은 PAY_009")
    void requestRefund_orderNotFound_throws() {
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("타인 주문 환불은 PAY_011")
    void requestRefund_otherUser_throws() {
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(completedOrder(999L)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("COMPLETED가 아닌 주문은 PAY_015")
    void requestRefund_nonCompleted_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.PENDING);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("환불 기간이 지난 주문은 PAY_010")
    void requestRefund_expiredPeriod_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusDays(8));
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("PENDING 환불이 이미 있으면 PAY_016")
    void requestRefund_duplicatePending_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "user request");
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.of(refund));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("동시 환불 요청으로 DB 제약 위반 시 PAY_016")
    void requestRefund_concurrentDuplicate_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ConstraintViolationException cve = Mockito.mock(ConstraintViolationException.class);
        Mockito.when(cve.getConstraintName()).thenReturn("uk_refunds_payment_order_id");
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.saveAndFlush(any(Refund.class)))
                .willThrow(new DataIntegrityViolationException("duplicate", cve));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("환불 내역 조회: 상태 필터")
    void getUserRefundHistory_statusFilter() {
        Refund refund = mockRefund(10L, RefundStatus.PENDING);
        given(refundRepository.findByUserIdWithFilter(eq(USER_ID), eq(RefundStatus.PENDING), eq(null), any(PageRequest.class)))
                .willReturn(List.of(refund));

        CursorPageResponse<UserRefundResponse> result =
                refundService.getUserRefundHistory(USER_ID, RefundStatus.PENDING, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    @DisplayName("환불 내역 조회: 다음 페이지 cursor 반환")
    void getUserRefundHistory_hasNext() {
        Refund r1 = mockRefund(10L, RefundStatus.PENDING);
        Refund r2 = mockRefund(9L, RefundStatus.APPROVED);
        Refund r3 = mock(Refund.class);
        given(refundRepository.findByUserIdWithFilter(eq(USER_ID), eq(null), eq(null), any(PageRequest.class)))
                .willReturn(List.of(r1, r2, r3));

        CursorPageResponse<UserRefundResponse> result =
                refundService.getUserRefundHistory(USER_ID, null, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(CursorUtils.encode(9L));
    }

    @Test
    @DisplayName("잘못된 환불 내역 cursor는 INVALID_CURSOR")
    void getUserRefundHistory_invalidCursor_throws() {
        assertThatThrownBy(() -> refundService.getUserRefundHistory(USER_ID, null, "not-valid-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("PENDING 환불 요청은 소유자가 취소할 수 있다")
    void cancelRefund_success() {
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        refundService.cancelRefund(USER_ID, 1L);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CANCELLED);
    }

    @Test
    @DisplayName("존재하지 않는 환불 취소는 PAY_018")
    void cancelRefund_notFound_throws() {
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    @DisplayName("타인 환불 취소는 PAY_011")
    void cancelRefund_otherUser_throws() {
        Refund refund = Refund.create(ORDER_ID, 999L, AMOUNT, "reason");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("PENDING이 아닌 환불 취소는 PAY_019")
    void cancelRefund_nonPending_throws() {
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        refund.approve();
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_CANCEL_NOT_ALLOWED);
    }

    private AtomicReference<Refund> stubRefundSave() {
        AtomicReference<Refund> saved = new AtomicReference<>();
        given(refundRepository.saveAndFlush(any(Refund.class))).willAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", REFUND_ID);
            saved.set(refund);
            return refund;
        });
        doAnswer(invocation -> Optional.ofNullable(saved.get()))
                .when(refundRepository).findByIdWithLock(REFUND_ID);
        return saved;
    }

    private Wallet wallet(long balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private PaymentOrder completedOrder(Long userId) {
        PaymentOrder order = PaymentOrder.create(ORDER_UID, userId, AMOUNT, "idem-key", PaymentMethod.CARD, "pg-order-id");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        return order;
    }

    private Refund mockRefund(Long id, RefundStatus status) {
        Refund refund = mock(Refund.class);
        given(refund.getId()).willReturn(id);
        given(refund.getPaymentOrderId()).willReturn(ORDER_ID);
        given(refund.getAmount()).willReturn(AMOUNT);
        given(refund.getStatus()).willReturn(status);
        given(refund.getReason()).willReturn("reason");
        given(refund.getCreatedAt()).willReturn(LocalDateTime.of(2026, 5, 25, 10, 0, 0));
        return refund;
    }
}
