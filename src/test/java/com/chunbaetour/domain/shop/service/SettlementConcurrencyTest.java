package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.SettlementRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class SettlementConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private SettlementService settlementService;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopWalletRepository shopWalletRepository;
    @Autowired private SettlementRepository settlementRepository;

    private static final long TEST_USER_ID = 9001L;

    @AfterEach
    void cleanup() {
        settlementRepository.deleteAll();
        shopWalletRepository.deleteAll();
        shopRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 정산 신청 2건 — ShopWallet 비관적 락으로 PENDING 1건만 생성")
    void concurrentSettlement_onlyOnePending() throws InterruptedException {
        // given: Shop + ShopWallet 세팅 (계좌 정보 포함)
        Shop shop = shopRepository.save(Shop.builder()
                .userId(TEST_USER_ID)
                .applicationId(9001L)
                .shopName("동시성테스트가게")
                .category("음식")
                .address("서울시 강남구")
                .build());

        ShopWallet wallet = ShopWallet.create(shop.getId());
        ReflectionTestUtils.setField(wallet, "balance", 50_000L);
        ReflectionTestUtils.setField(wallet, "bankName", "국민은행");
        ReflectionTestUtils.setField(wallet, "accountNumber", "123-456-789");
        ReflectionTestUtils.setField(wallet, "accountHolder", "홍길동");
        shopWalletRepository.save(wallet);

        // when: 2개 스레드 동시 정산 신청
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startLatch.await();
                settlementService.requestSettlement(TEST_USER_ID, shop.getId());
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.DUPLICATE_SETTLEMENT_REQUEST) {
                    duplicateCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(task);
        executor.submit(task);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executor.shutdownNow();

        // then: 정산 1건만 생성, 나머지 1건은 DUPLICATE로 차단
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
        assertThat(settlementRepository.count()).isEqualTo(1);
    }
}
