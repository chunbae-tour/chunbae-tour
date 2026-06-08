package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "portone.secret=test-secret",
        "portone.webhook-secret=test-webhook-secret",
        "portone.store-id=test-store",
        "portone.base-url=http://localhost:9999",
        "portone.channel.card=test-channel-card"
})
class CallbackServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 100L;
    private static final String ORDER_UID = "order-integration-1";
    private static final String IDEM_KEY = "idem-integration-key-1";
    private static final Long AMOUNT = 10_000L;

    @Autowired
    private CallbackService callbackService;

    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setup() {
        walletRepository.save(Wallet.create(USER_ID));
        redis.opsForValue().set("idempotency:" + IDEM_KEY, "1");
    }

    @AfterEach
    void cleanup() {
        yeopjeonHistoryRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
        var keys = redis.keys("idempotency:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("성공 콜백: payment_orders COMPLETED, wallet 잔액 증가, yeopjeon_histories INSERT")
    void handleSuccess_persists_completed_status_and_credits_wallet() {
        paymentOrderRepository.save(
                PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1"));
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null));

        callbackService.handleSuccess(ORDER_UID, "tx-1");

        // KAN-141 fix: DB-level CAS UPDATE로 전환되어 lock 없는 assertion으로 충분.
        // 기존 findByOrderUidWithLock 호출은 테스트 트랜잭션 밖에서 TransactionRequiredException 발생.
        PaymentOrder order = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(order.getPgTransactionId()).isEqualTo("tx-1");

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);

        assertThat(yeopjeonHistoryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("실패 콜백: payment_orders FAILED, Redis 멱등키 삭제")
    void handleFail_persists_failed_status_and_removes_idempotency_key() {
        paymentOrderRepository.save(
                PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1"));

        callbackService.handleFail(ORDER_UID);

        PaymentOrder order = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);

        assertThat(redis.hasKey("idempotency:" + IDEM_KEY)).isFalse();
    }

    @Test
    @DisplayName("[REFUNDED 회귀 가드] 환불 완료된 주문에 PAID webhook 재전송 → 재완료 차단 + wallet 잔액 미증가")
    void handleSuccess_refunded_order_rejects_recompletion() {
        // PR #198 코드 리뷰 (CodeRabbit + lim-haeun) 반영:
        // 기존 WHERE 절(status <> COMPLETED)은 REFUNDED 상태에서도 CAS UPDATE를 허용해 재완료 위험.
        // WHERE 절을 IN (PENDING, FAILED)로 명시적 화이트리스트 적용 — REFUNDED는 차단.
        PaymentOrder order = PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1");
        order.complete("tx-original");
        order.refund();
        paymentOrderRepository.save(order);
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null));

        // 1단계 조기 리턴(COMPLETED)에서 차단되지 않도록 REFUNDED 상태로 직접 진입 — CAS UPDATE 차단 검증이 본 테스트 목적.
        callbackService.handleSuccess(ORDER_UID, "tx-resend");

        PaymentOrder reloaded = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(reloaded.getPgTransactionId()).isEqualTo("tx-original");

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isZero();
        assertThat(yeopjeonHistoryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("[Critical #2 회귀 가드] COMPLETED 주문에 성공 콜백 재전송: afterCommit 장애로 남은 멱등키 정리 보장")
    void handleSuccess_already_completed_still_releases_idempotency_key_after_commit() {
        // 시나리오: 이전 처리에서 DB commit 성공 후 afterCommit() 실행 전 장애 발생
        //   → 멱등키가 Redis에 남아있는 상태로 웹훅 재전송
        //   → COMPLETED 조기 리턴이어도 scheduleUnmark로 키 정리 보장
        PaymentOrder order = PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1");
        order.complete("tx-original");
        paymentOrderRepository.save(order);
        redis.opsForValue().set("idempotency:" + IDEM_KEY, "1");

        callbackService.handleSuccess(ORDER_UID, "tx-resend");

        assertThat(redis.hasKey("idempotency:" + IDEM_KEY)).isFalse();
        PaymentOrder reloaded = paymentOrderRepository.findByOrderUid(ORDER_UID).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("[#114 release blocker 회귀 가드] 성공 콜백 동시 2회: DB-level CAS UPDATE로 wallet 잔액 정확히 1회만 증가")
    void handleSuccess_concurrent_double_call_credits_wallet_exactly_once() throws InterruptedException {
        paymentOrderRepository.save(
                PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1"));
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT, null));

        CountDownLatch latch = new CountDownLatch(2);
        List<Exception> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        Runnable task = () -> {
            try {
                callbackService.handleSuccess(ORDER_UID, "tx-1");
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(task);
        executor.submit(task);
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
        assertThat(yeopjeonHistoryRepository.findAll()).hasSize(1);
        assertThat(errors).isEmpty();
    }
}
