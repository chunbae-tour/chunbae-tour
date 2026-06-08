package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AdminRefundService 통합 테스트.
 * 핵심 목적: 단일 트랜잭션 내 PG 취소 실행 — 성공 시 전체 커밋, 실패 시 전체 롤백 검증.
 * 단위 테스트(AdminRefundServiceTest)에서는 실제 트랜잭션 롤백을 검증할 수 없어 보완한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "portone.webhook-secret=test-webhook-secret",
        "portone.base-url=http://localhost:9999"
})
class AdminRefundServiceIntegrationTest extends AbstractIntegrationTest {

    // CallbackServiceIntegrationTest는 USER_ID=100L 사용 — 컨테이너 공유 시 충돌 방지
    private static final Long USER_ID = 201L;
    private static final Long AMOUNT = 10_000L;

    @Autowired
    private AdminRefundService adminRefundService;

    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @AfterEach
    void cleanup() {
        refundRepository.deleteAll();
        yeopjeonHistoryRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fixture helpers
    // ──────────────────────────────────────────────────────────────────────────

    private PaymentOrder saveCompletedOrder() {
        PaymentOrder order = PaymentOrder.create(
                "refund-it-uid-1", USER_ID, AMOUNT, "refund-it-idem-1", PaymentMethod.CARD, "pg-order-it-1");
        order.complete("pg-tx-it-1");
        return paymentOrderRepository.save(order);
    }

    private void saveWalletWithBalance(long balance) {
        Wallet wallet = Wallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        walletRepository.save(wallet);
    }

    private Refund savePendingRefund(Long paymentOrderId) {
        return refundRepository.save(Refund.create(paymentOrderId, USER_ID, AMOUNT, "단순 변심"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 트랜잭션 정합성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("승인 성공: PG 취소까지 단일 트랜잭션으로 커밋 — Refund APPROVED, PaymentOrder REFUNDED, 엽전 차감")
    void approveRefund_success_all_committed() {
        PaymentOrder order = saveCompletedOrder();
        saveWalletWithBalance(AMOUNT);
        Refund refund = savePendingRefund(order.getId());
        willDoNothing().given(paymentGatewayClient).cancelPayment(any(), any(), any(), any());

        RefundDetailResponse response = adminRefundService.approveRefund(refund.getId());

        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);

        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        PaymentOrder savedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);

        Wallet savedWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(savedWallet.getBalance()).isZero();

        verify(paymentGatewayClient).cancelPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PG 취소 실패 시 트랜잭션 전체 롤백 — Refund PENDING, PaymentOrder COMPLETED, 엽전 차감 없음")
    void approveRefund_pg_cancel_fails_rolls_back_all() {
        PaymentOrder order = saveCompletedOrder();
        saveWalletWithBalance(AMOUNT);
        Refund refund = savePendingRefund(order.getId());
        willThrow(new RuntimeException("PG timeout"))
                .given(paymentGatewayClient).cancelPayment(any(), any(), any(), any());

        assertThatThrownBy(() -> adminRefundService.approveRefund(refund.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PG timeout");

        // 트랜잭션 롤백 — 모든 DB 상태 원상 복구
        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.PENDING);

        PaymentOrder savedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);

        // 엽전 회수도 롤백 — 잔액 원상 복구
        Wallet savedWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(savedWallet.getBalance()).isEqualTo(AMOUNT);
    }
}
