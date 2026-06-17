package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopImageType;
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
import org.springframework.mock.web.MockMultipartFile;

/**
 * PROFILE "가게당 1장" 불변식의 동시성 검증 (KAN-315, PR #589 리뷰 Major).
 *
 * <p>{@code replaceProfile}의 read(기존 PROFILE)-delete-insert는 무잠금이면 동시 업로드 2건이 같은 집합을 읽어
 * 각각 insert → PROFILE 2장 잔존(대표 비결정)이 된다. Shop 행 비관락(SELECT ... FOR UPDATE)으로 직렬화하므로,
 * 동시 2건이 들어와도 최종 PROFILE 행은 정확히 1개여야 한다.
 */
@SpringBootTest
class ShopImageProfileConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private ShopImageService shopImageService;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopImageRepository shopImageRepository;

    private static final long TEST_USER_ID = 8201L;

    @AfterEach
    void cleanup() {
        shopImageRepository.deleteAll();
        shopRepository.deleteAll();
    }

    /** JPEG 매직바이트(FF D8 FF) 더미 — magic-byte 검증 통과용. 스레드별 새 인스턴스(스트림 독립). */
    private MockMultipartFile jpeg() {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", b);
    }

    @Test
    @DisplayName("동시 PROFILE 업로드 2건 — Shop 행 비관락으로 직렬화 → PROFILE 행 정확히 1개")
    void concurrentProfileUpload_keepsSingleRow() throws InterruptedException {
        Shop shop = shopRepository.save(Shop.builder()
                .userId(TEST_USER_ID).applicationId(8201L)
                .shopName("PROFILE동시성").category("FOOD")
                .address("서울시 강남구").build());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startLatch.await();
                shopImageService.uploadImage(TEST_USER_ID, shop.getId(), ShopImageType.PROFILE, jpeg());
                successCount.incrementAndGet();
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

        // 둘 다 성공(교체 의미라 예외 없음)하되, 직렬화로 최종 PROFILE 행은 1개만 잔존
        assertThat(successCount.get()).isEqualTo(2);
        java.util.List<ShopImage> profiles =
                shopImageRepository.findByShopIdAndType(shop.getId(), ShopImageType.PROFILE);
        assertThat(profiles).hasSize(1);
    }
}
