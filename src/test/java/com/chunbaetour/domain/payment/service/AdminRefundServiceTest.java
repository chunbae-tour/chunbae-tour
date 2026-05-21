package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminRefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private AdminRefundService adminRefundService;

    private static final Long REFUND_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 10_000L;

    private Refund makePendingRefund() {
        Refund refund = Refund.create(ORDER_ID, USER_ID, AMOUNT, "단순 변심");
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private PaymentOrder makeCompletedOrder() {
        PaymentOrder order = PaymentOrder.create("uid-1", USER_ID, AMOUNT, "idem-1", PaymentMethod.CARD, "pg-order-1");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "pgTransactionId", "pg-txn-123");
        return order;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // approveRefund
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("환불 승인 성공: PG 취소 → 엽전 차감 → 상태 APPROVED")
    void approveRefund_success() {
        Refund refund = makePendingRefund();
        PaymentOrder order = makeCompletedOrder();
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));
        given(paymentOrderRepository.findByIdWithLock(ORDER_ID)).willReturn(Optional.of(order));
        willDoNothing().given(paymentGatewayClient).cancelPayment(any(), any(), any());
        willDoNothing().given(walletService).refund(any(), any(), any());

        RefundDetailResponse response = adminRefundService.approveRefund(REFUND_ID);

        verify(paymentGatewayClient).cancelPayment(eq("pg-txn-123"), eq(AMOUNT), any());
        verify(walletService).refund(USER_ID, AMOUNT, ORDER_ID);
        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("존재하지 않는 환불 ID 승인 시 PAYMENT_HISTORY_NOT_FOUND 예외")
    void approveRefund_refund_not_found() {
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminRefundService.approveRefund(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);

        verifyNoInteractions(paymentGatewayClient, walletService);
    }

    @Test
    @DisplayName("PENDING이 아닌 환불 승인 시 DUPLICATE_PAYMENT_REQUEST 예외")
    void approveRefund_not_pending_throws() {
        Refund refund = makePendingRefund();
        refund.approve(); // APPROVED로 전이
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> adminRefundService.approveRefund(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);

        verifyNoInteractions(paymentGatewayClient, walletService);
    }

    @Test
    @DisplayName("엽전 잔액 부족 시 INSUFFICIENT_BALANCE 예외 — PG 취소는 이미 실행됨")
    void approveRefund_insufficient_wallet_throws() {
        Refund refund = makePendingRefund();
        PaymentOrder order = makeCompletedOrder();
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));
        given(paymentOrderRepository.findByIdWithLock(ORDER_ID)).willReturn(Optional.of(order));
        willDoNothing().given(paymentGatewayClient).cancelPayment(any(), any(), any());
        willThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE))
                .given(walletService).refund(any(), any(), any());

        assertThatThrownBy(() -> adminRefundService.approveRefund(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // rejectRefund
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("환불 거절 성공: 상태 REJECTED, PG 호출 없음")
    void rejectRefund_success() {
        Refund refund = makePendingRefund();
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));

        RefundDetailResponse response = adminRefundService.rejectRefund(REFUND_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.REJECTED);
        verifyNoInteractions(paymentGatewayClient, walletService);
    }

    @Test
    @DisplayName("PENDING이 아닌 환불 거절 시 DUPLICATE_PAYMENT_REQUEST 예외")
    void rejectRefund_not_pending_throws() {
        Refund refund = makePendingRefund();
        refund.reject(); // 이미 REJECTED
        given(refundRepository.findByIdWithLock(REFUND_ID)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> adminRefundService.rejectRefund(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getRefunds (cursor pagination)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cursor 없이 조회 시 최신순 첫 페이지 반환")
    void getRefunds_no_cursor_returns_first_page() {
        Refund r1 = makePendingRefund();
        ReflectionTestUtils.setField(r1, "id", 2L);
        Refund r2 = makePendingRefund();
        ReflectionTestUtils.setField(r2, "id", 1L);
        given(refundRepository.findAllOrderByIdDesc(any())).willReturn(List.of(r1, r2));

        CursorPageResponse<RefundDetailResponse> page = adminRefundService.getRefunds(null, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("결과가 size+1이면 hasNext=true이고 nextCursor가 설정된다")
    void getRefunds_has_next_page() {
        List<Refund> refunds = new java.util.ArrayList<>();
        for (long i = 5; i >= 1; i--) {
            Refund r = makePendingRefund();
            ReflectionTestUtils.setField(r, "id", i);
            refunds.add(r);
        }
        given(refundRepository.findAllOrderByIdDesc(any())).willReturn(refunds); // 5개 반환 (size=4 요청)

        CursorPageResponse<RefundDetailResponse> page = adminRefundService.getRefunds(null, 4);

        assertThat(page.content()).hasSize(4);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("잘못된 cursor 형식 디코딩 시 INVALID_REQUEST 예외")
    void getRefunds_invalid_cursor_throws() {
        assertThatThrownBy(() -> adminRefundService.getRefunds("!!invalid!!", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("유효한 cursor로 조회 시 findByIdLessThan 호출")
    void getRefunds_with_valid_cursor() {
        String cursorJson = "{\"id\":50}";
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(cursorJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        given(refundRepository.findByIdLessThanOrderByIdDesc(eq(50L), any())).willReturn(List.of());

        CursorPageResponse<RefundDetailResponse> page = adminRefundService.getRefunds(cursor, 10);

        verify(refundRepository).findByIdLessThanOrderByIdDesc(eq(50L), any());
        assertThat(page.content()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}
