package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.request.StorePurchaseRequest;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.StoreOrderRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

@SpringBootTest
class StorePurchaseConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int STOCK = 3;
    private static final int REQUEST_COUNT = 10;
    private static final long USER_ID_BASE = 9_000L;

    @Autowired private StorePurchaseService storePurchaseService;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreOrderRepository storeOrderRepository;
    @Autowired private UserItemRepository userItemRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private YeopjeonHistoryRepository yeopjeonHistoryRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        userItemRepository.deleteAll();
        storeOrderRepository.deleteAll();
        yeopjeonHistoryRepository.deleteAll();
        walletRepository.deleteAll();
        productRepository.deleteAll();
        deleteRedisKeys("stock:*");
        deleteRedisKeys("product:*");
    }

    @Test
    @DisplayName("동시 구매 요청이 재고를 초과해도 성공 주문 수와 DB 재고가 음수가 되지 않음")
    void concurrentPurchase_doesNotOversell() throws Exception {
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("한정 수량 상품")
                .description("동시성 테스트 상품")
                .category("TEST")
                .price(1_000L)
                .stock(STOCK)
                .originalStock(STOCK)
                .merchantName("테스트 상인")
                .validityDays(30)
                .status(ProductStatus.ON_SALE)
                .maxPerPerson(1)
                .build());
        Long productId = product.getId();
        redisTemplate.opsForValue().set("stock:" + productId, String.valueOf(STOCK));

        for (int i = 0; i < REQUEST_COUNT; i++) {
            Wallet wallet = Wallet.create(USER_ID_BASE + i);
            wallet.credit(10_000L);
            walletRepository.save(wallet);
        }
        walletRepository.flush();

        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long userId = USER_ID_BASE + i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        storePurchaseService.purchase(userId, new StorePurchaseRequest(productId, 1));
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);
                        return false;
                    }
                }, executor));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        long successCount = futures.stream().filter(CompletableFuture::join).count();
        Product updated = productRepository.findById(productId).orElseThrow();

        assertThat(successCount).isEqualTo(STOCK);
        assertThat(storeOrderRepository.count()).isEqualTo(STOCK);
        assertThat(userItemRepository.count()).isEqualTo(STOCK);
        assertThat(yeopjeonHistoryRepository.count()).isEqualTo(STOCK);
        assertThat(updated.getStock()).isZero();
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
