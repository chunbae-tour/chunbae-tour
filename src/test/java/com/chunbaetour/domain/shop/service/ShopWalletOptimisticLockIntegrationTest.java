package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ShopWallet @Version 낙관적 락 통합 테스트 (KAN-244).
 * 단위 테스트로는 재현 불가한 트랜잭션 커밋 시점의 잔액 lost-update를 검증.
 * Testcontainers(MySQL) 기반 — Docker 실행 필요.
 */
@SpringBootTest
class ShopWalletOptimisticLockIntegrationTest extends AbstractIntegrationTest {

    // 다른 통합테스트와 shopId 충돌 방지
    private static final Long SHOP_ID = 9001L;

    @Autowired
    private ShopWalletRepository shopWalletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        shopWalletRepository.deleteAll();
    }

    @Test
    @DisplayName("credit 커밋 후 stale 엔티티로 재커밋 시 ObjectOptimisticLockingFailureException 발생")
    void 동시_잔액_변경_충돌_시_낙관적_락_예외_발생() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 1. ShopWallet 저장 — version=0
        ShopWallet wallet = ShopWallet.create(SHOP_ID);
        txTemplate.execute(s -> {
            shopWalletRepository.save(wallet);
            return null;
        });
        Long walletId = wallet.getId();

        // 2. 트랜잭션A: stale 엔티티(version=0) 참조 유지
        ShopWallet staleWallet = shopWalletRepository.findById(walletId).orElseThrow();

        // 3. 트랜잭션B: credit 커밋 → DB version=1
        txTemplate.execute(s -> {
            ShopWallet fresh = shopWalletRepository.findById(walletId).orElseThrow();
            fresh.credit(1000L);
            return null; // 더티체킹으로 version=1 UPDATE
        });

        // 4. 트랜잭션A: stale 엔티티(version=0)로 저장 시도 → 버전 불일치 충돌
        assertThatThrownBy(() -> txTemplate.execute(s -> {
            staleWallet.credit(500L);
            shopWalletRepository.save(staleWallet); // version=0, DB version=1 → 충돌
            return null;
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("단일 트랜잭션에서 credit 성공 시 version이 증가하고 잔액이 반영된다")
    void 정상_credit_시_version_증가_및_잔액_반영() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        ShopWallet wallet = ShopWallet.create(SHOP_ID);
        txTemplate.execute(s -> {
            shopWalletRepository.save(wallet);
            return null;
        });
        Long walletId = wallet.getId();

        txTemplate.execute(s -> {
            ShopWallet fresh = shopWalletRepository.findById(walletId).orElseThrow();
            fresh.credit(2000L);
            return null;
        });

        ShopWallet updated = shopWalletRepository.findById(walletId).orElseThrow();
        assertThat(updated.getBalance()).isEqualTo(2000L);
        assertThat((Long) ReflectionTestUtils.getField(updated, "version")).isEqualTo(1L);
    }
}
