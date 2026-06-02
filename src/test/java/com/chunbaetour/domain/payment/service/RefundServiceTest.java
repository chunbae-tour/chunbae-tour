package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
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

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;
    @Mock private Clock clock;
    @Mock private TransactionTemplate transactionTemplate;

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
    @DisplayName("전액 잔액 환불 요청 → PENDING 접수, PG 호출 없음")
    void requestRefund_fullBalance_returnsPendingRefund() {
        PaymentOrder order = completedOrder(USER_ID);
        stubRefundSave();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request"));

        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);
        verify(refundRepository).saveAndFlush(any(Refund.class));
        verify(walletService, never()).reclaimAvailableForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("부분 사용 후 환불 요청 → REJECTED 기록 저장 + REFUND_BALANCE_INSUFFICIENT 에러")
    void requestRefund_partialUse_savesRejectedRecordAndThrows() {
        PaymentOrder order = completedOrder(USER_ID);
        AtomicReference<Refund> refundRef = new AtomicReference<>();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT - 1)));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> {
            Refund r = inv.getArgument(0);
            refundRef.set(r);
            return r;
        });

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("user request")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_BALANCE_INSUFFICIENT);

        assertThat(refundRef.get().getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(refundRef.get().getRejectReason()).isEqualTo("부분환불은 불가합니다");
        verify(walletService, never()).reclaimAvailableForRefund(any(), any(), any());
    }

    @Test
    @DisplayName("이미 REFUNDED 주문 + APPROVED 환불 이력 → 멱등 응답")
    void requestRefund_alreadyRefunded_returnsApprovedIdempotent() {
        PaymentOrder order = completedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.REFUNDED);
        Refund approvedRefund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        approvedRefund.approve();
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(approvedRefund));

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason"));

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("존재하지 않는 주문 → PAY_009")
    void requestRefund_orderNotFound_throws() {
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("타인 주문 환불 → PAY_011")
    void requestRefund_otherUserOrder_throws() {
        PaymentOrder order = completedOrder(999L);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("COMPLETED 아닌 주문 환불 → PAY_014")
    void requestRefund_nonCompletedOrder_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.CANCELLED);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("환불 기간 만료 → PAY_010")
    void requestRefund_expiredPeriod_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusDays(8));
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("PENDING 중복 요청 → PAY_016")
    void requestRefund_duplicatePending_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        Refund pendingRefund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(pendingRefund));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("동시 환불 요청 UK 위반 → PAY_016")
    void requestRefund_concurrentDuplicate_throws() {
        PaymentOrder order = completedOrder(USER_ID);
        ConstraintViolationException cve = Mockito.mock(ConstraintViolationException.class);
        Mockito.when(cve.getConstraintName()).thenReturn("uk_refunds_payment_order_id");
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(ORDER_ID)).willReturn(Optional.empty());
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(AMOUNT)));
        given(refundRepository.saveAndFlush(any(Refund.class)))
                .willThrow(new DataIntegrityViolationException("duplicate", cve));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("스케줄러 완료: PENDING → APPROVED, 엽전 회수, 주문 REFUNDED")
    void completeSchedulerRetry_pendingRefund_approvesAndReclaims() {
        PaymentOrder order = completedOrder(USER_ID);
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(AMOUNT);

        refundService.completeSchedulerRetry(REFUND_ID, ORDER_UID, AMOUNT);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("스케줄러 완료: FAILED → APPROVED (재시도 성공)")
    void completeSchedulerRetry_failedRefund_approvesOnRetry() {
        PaymentOrder order = completedOrder(USER_ID);
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        ReflectionTestUtils.setField(refund, "status", RefundStatus.FAILED);
        given(paymentOrderRepository.findByOrderUidWithLock(ORDER_UID)).willReturn(Optional.of(order));
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));
        given(walletService.reclaimAvailableForRefund(USER_ID, AMOUNT, ORDER_ID)).willReturn(AMOUNT);

        refundService.completeSchedulerRetry(REFUND_ID, ORDER_UID, AMOUNT);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("환불 내역 조회")
    void getUserRefundHistory_statusFilter() {
        Refund refund = mockRefund(10L, RefundStatus.PENDING);
        given(refundRepository.findByUserIdWithFilter(eq(USER_ID), eq(RefundStatus.PENDING), eq(null), any(PageRequest.class)))
                .willReturn(List.of(refund));

        CursorPageResponse<UserRefundResponse> result =
                refundService.getUserRefundHistory(USER_ID, RefundStatus.PENDING, null, 20);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("잘못된 cursor → INVALID_CURSOR")
    void getUserRefundHistory_invalidCursor_throws() {
        assertThatThrownBy(() -> refundService.getUserRefundHistory(USER_ID, null, "not-valid-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("PENDING 환불 취소 성공")
    void cancelRefund_success() {
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "reason");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        refundService.cancelRefund(USER_ID, 1L);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CANCELLED);
    }

    @Test
    @DisplayName("존재하지 않는 환불 취소 → PAY_018")
    void cancelRefund_notFound_throws() {
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    @DisplayName("타인 환불 취소 → PAY_011")
    void cancelRefund_otherUser_throws() {
        Refund refund = Refund.create(ORDER_ID, 999L, AMOUNT, "reason");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("PENDING 아닌 환불 취소 → PAY_019")
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
