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
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT));

        callbackService.handleSuccess(ORDER_UID, "tx-1");

        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(ORDER_UID).orElseThrow();
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

        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(ORDER_UID).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);

        assertThat(redis.hasKey("idempotency:" + IDEM_KEY)).isFalse();
    }

    @Test
    @DisplayName("성공 콜백 동시 2회: 비관적 락으로 wallet 잔액 정확히 1회만 증가")
    void handleSuccess_concurrent_double_call_credits_wallet_exactly_once() throws InterruptedException {
        paymentOrderRepository.save(
                PaymentOrder.create(ORDER_UID, USER_ID, AMOUNT, IDEM_KEY, PaymentMethod.CARD, "pg-1"));
        given(paymentGatewayClient.verifyPayment(ORDER_UID))
                .willReturn(new PortOnePaymentInfo("PAID", AMOUNT));

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
