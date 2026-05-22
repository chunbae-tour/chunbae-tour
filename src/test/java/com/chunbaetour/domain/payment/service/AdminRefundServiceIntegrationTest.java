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
 * 핵심 목적: @Transactional + afterCommit 경계에서 PG 취소 호출 순서 및 DB 상태 정합성 검증.
 * 단위 테스트(AdminRefundServiceTest)는 TransactionSynchronizationManager.isSynchronizationActive() == false로
 * afterCommit 경로를 탈 수 없으므로, 실제 트랜잭션이 커밋되는 통합 환경에서 보완 검증한다.
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
    // afterCommit 경계 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("승인 성공: DB 커밋 후 afterCommit에서 PG 취소 호출, DB 상태 APPROVED/CANCELLED/차감 확인")
    void approveRefund_commits_db_then_calls_pg_in_afterCommit() {
        PaymentOrder order = saveCompletedOrder();
        saveWalletWithBalance(AMOUNT);
        Refund refund = savePendingRefund(order.getId());
        willDoNothing().given(paymentGatewayClient).cancelPayment(any(), any(), any());

        RefundDetailResponse response = adminRefundService.approveRefund(refund.getId());

        // 반환값 상태 확인
        assertThat(response.status()).isEqualTo(RefundStatus.APPROVED);

        // DB 커밋 확인 — Refund APPROVED, PaymentOrder CANCELLED
        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        PaymentOrder savedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);

        // 엽전 차감 확인 (AMOUNT → 0)
        Wallet savedWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(savedWallet.getBalance()).isZero();

        // afterCommit에서 PG 취소 호출 확인
        verify(paymentGatewayClient).cancelPayment(any(), any(), any());
    }

    @Test
    @DisplayName("PG 취소 afterCommit 실패: DB는 이미 커밋(APPROVED/차감 유지), PG 예외 전파")
    void approveRefund_pg_cancel_fails_in_afterCommit_db_stays_committed() {
        PaymentOrder order = saveCompletedOrder();
        saveWalletWithBalance(AMOUNT);
        Refund refund = savePendingRefund(order.getId());
        willThrow(new RuntimeException("PG timeout"))
                .given(paymentGatewayClient).cancelPayment(any(), any(), any());

        // afterCommit 내 예외는 Spring finally 블록에서 호출자에게 전파됨
        assertThatThrownBy(() -> adminRefundService.approveRefund(refund.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PG timeout");

        // DB 커밋은 PG 호출 전에 완료 — 롤백 불가, APPROVED 상태 유지
        Refund savedRefund = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // 엽전 차감도 커밋 완료 — 0 유지
        Wallet savedWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(savedWallet.getBalance()).isZero();
    }
}
