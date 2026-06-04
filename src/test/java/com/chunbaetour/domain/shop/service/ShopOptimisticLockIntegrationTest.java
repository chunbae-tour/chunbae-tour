package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * Shop @Version 낙관적 락 통합 테스트 (STORY-10).
 * 단위 테스트(Mockito)로는 재현 불가한 트랜잭션 커밋 시점의 버전 충돌을 검증.
 * Testcontainers(MySQL) 기반 — Docker 실행 필요.
 */
@SpringBootTest
class ShopOptimisticLockIntegrationTest extends AbstractIntegrationTest {

    // 다른 통합테스트(USER_ID=100L/201L)와 충돌 방지
    private static final Long USER_ID = 301L;
    private static final Long APPLICATION_ID = 301L;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        shopRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 수정 요청 시 나중에 커밋하는 트랜잭션에서 ObjectOptimisticLockingFailureException 발생")
    void 동시_수정_충돌_시_낙관적_락_예외_발생() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 1. Shop 저장 — version=0 으로 커밋
        Shop shop = createShop();
        txTemplate.execute(s -> {
            shopRepository.save(shop);
            return null;
        });
        Long shopId = shop.getId();

        // 2. 트랜잭션A: 최신 Shop 조회 후 수정 커밋 → DB version=1
        txTemplate.execute(s -> {
            Shop fresh = shopRepository.findById(shopId).orElseThrow();
            ShopUpdateRequest req = new ShopUpdateRequest(
                    "수정된가게A", null, null, null, null, null, null);
            fresh.update(req);
            return null; // 더티체킹으로 version=1 UPDATE
        });

        // 3. 트랜잭션B: version=0인 stale 엔티티로 저장 시도 → 버전 불일치 충돌
        assertThatThrownBy(() -> txTemplate.execute(s -> {
            shopRepository.save(shop); // shop.version=0, DB version=1 → 충돌
            return null;
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("단일 트랜잭션에서 정상 수정 시 version이 증가한다")
    void 정상_수정_시_version_증가() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Shop shop = createShop();
        txTemplate.execute(s -> {
            shopRepository.save(shop);
            return null;
        });
        Long shopId = shop.getId();

        // 정상 수정
        txTemplate.execute(s -> {
            Shop fresh = shopRepository.findById(shopId).orElseThrow();
            ShopUpdateRequest req = new ShopUpdateRequest(
                    "수정된가게", null, null, null, null, null, null);
            fresh.update(req);
            return null;
        });

        Shop updated = shopRepository.findById(shopId).orElseThrow();
        assertThat(updated.getShopName()).isEqualTo("수정된가게");
        assertThat((Long) ReflectionTestUtils.getField(updated, "version")).isEqualTo(1L);
    }

    private Shop createShop() {
        MerchantApplication app = MerchantApplication.create(USER_ID,
                new MerchantApplyRequest(
                        "테스트가게", "101-81-34618", "한식", "서울시 강남구",
                        new BigDecimal("37.5665"), new BigDecimal("126.9780"),
                        null, null));
        ReflectionTestUtils.setField(app, "id", APPLICATION_ID);
        return Shop.fromApplication(app, 1L);
    }
}
