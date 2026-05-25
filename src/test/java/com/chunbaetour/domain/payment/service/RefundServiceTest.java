package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.Mockito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private RefundService refundService;

    private static final Long USER_ID = 1L;
    private static final String ORDER_UID = "test-order-uid";
    private static final Long AMOUNT = 10_000L;

    private Wallet makeWallet(long balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private PaymentOrder makeCompletedOrder(Long userId) {
        PaymentOrder order = PaymentOrder.create(ORDER_UID, userId, AMOUNT, "idem-key", PaymentMethod.CARD, "pg-order-id");
        // JPA 어노테이션(@CreatedDate 등)은 unit test에서 자동 주입 안 되므로 직접 설정
        ReflectionTestUtils.setField(order, "id", 100L);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        return order;
    }

    @Test
    @DisplayName("정상 환불 요청 시 PENDING 상태 Refund가 생성된다")
    void requestRefund_success_creates_pending_refund() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(makeWallet(AMOUNT)));
        given(refundRepository.existsByPaymentOrderIdAndStatus(100L, RefundStatus.PENDING)).willReturn(false);
        given(refundRepository.saveAndFlush(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));

        RefundResponse response = refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("단순 변심"));

        verify(refundRepository).saveAndFlush(any(Refund.class));
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 PAY_009(PAYMENT_HISTORY_NOT_FOUND)를 던진다")
    void requestRefund_order_not_found_throws_PAY_009() {
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("타인 주문 환불 요청 시 PAY_011(PAYMENT_HISTORY_FORBIDDEN)를 던진다")
    void requestRefund_other_user_order_throws_PAY_011() {
        PaymentOrder order = makeCompletedOrder(999L); // 다른 유저 주문
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("COMPLETED가 아닌 주문 환불 요청 시 PAY_015(REFUND_NOT_ELIGIBLE)를 던진다")
    void requestRefund_non_completed_order_throws_PAY_015() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.PENDING);
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("환불 기간(7일) 초과 시 PAY_010(REFUND_PERIOD_EXPIRED)를 던진다")
    void requestRefund_expired_period_throws_PAY_010() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        // createdAt을 8일 전으로 설정
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusDays(8));
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("동시 환불 요청으로 DB 제약 위반 시 PAY_016(DUPLICATE_REFUND_REQUEST)를 던진다")
    void requestRefund_concurrent_request_throws_PAY_016() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(makeWallet(AMOUNT)));
        given(refundRepository.existsByPaymentOrderIdAndStatus(100L, RefundStatus.PENDING)).willReturn(false);

        // ConstraintViolationException cause 세팅 — 서비스가 constraintName으로 분기하므로 필수
        ConstraintViolationException cve = Mockito.mock(ConstraintViolationException.class);
        Mockito.when(cve.getConstraintName()).thenReturn("uk_refunds_payment_order_id");
        given(refundRepository.saveAndFlush(any(Refund.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate", cve));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("단순 변심")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("엽전을 일부 사용한 경우 PAY_017(REFUND_BALANCE_INSUFFICIENT)를 던진다")
    void requestRefund_partial_use_throws_PAY_017() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(makeWallet(AMOUNT - 1)));

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest("단순 변심")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("이미 PENDING 환불 요청이 있으면 PAY_016(DUPLICATE_REFUND_REQUEST)를 던진다")
    void requestRefund_duplicate_pending_throws_PAY_016() {
        PaymentOrder order = makeCompletedOrder(USER_ID);
        given(paymentOrderRepository.findByOrderUid(ORDER_UID)).willReturn(Optional.of(order));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(makeWallet(AMOUNT)));
        given(refundRepository.existsByPaymentOrderIdAndStatus(100L, RefundStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> refundService.requestRefund(USER_ID, ORDER_UID, new RefundRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("PENDING 환불 요청 취소 시 상태가 CANCELLED로 변경된다")
    void cancelRefund_success_changes_status_to_cancelled() {
        Refund refund = Refund.create(100L, USER_ID, AMOUNT, "단순 변심");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        refundService.cancelRefund(USER_ID, 1L);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CANCELLED);
    }

    @Test
    @DisplayName("존재하지 않는 환불 요청 취소 시 PAY_018(REFUND_NOT_FOUND)를 던진다")
    void cancelRefund_not_found_throws_PAY_018() {
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    @DisplayName("타인의 환불 요청 취소 시 PAY_011(PAYMENT_HISTORY_FORBIDDEN)를 던진다")
    void cancelRefund_other_user_throws_PAY_011() {
        Refund refund = Refund.create(100L, 999L, AMOUNT, "단순 변심");
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
    }

    @Test
    @DisplayName("PENDING이 아닌 환불 요청 취소 시 PAY_019(REFUND_CANCEL_NOT_ALLOWED)를 던진다")
    void cancelRefund_non_pending_throws_PAY_019() {
        Refund refund = Refund.create(100L, USER_ID, AMOUNT, "단순 변심");
        ReflectionTestUtils.setField(refund, "status", RefundStatus.APPROVED);
        given(refundRepository.findByIdWithLock(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundService.cancelRefund(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_CANCEL_NOT_ALLOWED);
    }
}
