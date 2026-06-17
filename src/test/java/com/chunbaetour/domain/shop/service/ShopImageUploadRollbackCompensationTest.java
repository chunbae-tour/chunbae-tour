package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import com.chunbaetour.domain.shop.type.ShopImageType;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 업로드 롤백 보상 통합 테스트 (KAN-319 ①).
 *
 * <p>S3 업로드 성공 후 DB 영속화가 실패해 트랜잭션이 롤백되면, 방금 올린 객체가 고아가 된다.
 * {@code uploadImage}가 등록한 afterCompletion(ROLLED_BACK) 보상 훅이 그 객체를 즉시 삭제하는지 검증한다.
 * 저장소·이미지 레포는 mock으로 두되(업로드 성공/영속화 실패를 강제), 실 트랜잭션 매니저로 롤백 동기화를 실제로 발화시킨다.
 */
@SpringBootTest
class ShopImageUploadRollbackCompensationTest extends AbstractIntegrationTest {

    @Autowired private ShopImageService shopImageService;
    @Autowired private ShopRepository shopRepository;

    @MockitoBean private ShopImageStorage imageStorage;
    @MockitoBean private ShopImageRepository shopImageRepository;

    private static final long USER_ID = 8301L;

    @AfterEach
    void cleanup() {
        shopRepository.deleteAll();
    }

    private MockMultipartFile jpeg() {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", b);
    }

    @Test
    @DisplayName("S3 업로드 후 DB 영속화 실패로 롤백 → 방금 올린 객체를 보상 삭제")
    void uploadRollback_compensatesByDeletingObject() {
        Shop shop = shopRepository.save(Shop.builder()
                .userId(USER_ID).applicationId(USER_ID)
                .shopName("롤백보상").category("FOOD")
                .address("서울시 강남구").build());

        String uploadedKey = "shops/" + shop.getId() + "/rollback.jpg";
        given(imageStorage.upload(anyLong(), any())).willReturn(uploadedKey);
        // GALLERY 경로 — 잠금 조회는 빈 목록, save에서 영속화 실패를 강제해 트랜잭션 롤백 유발
        given(shopImageRepository.findByShopIdAndTypeForUpdate(shop.getId(), ShopImageType.GALLERY))
                .willReturn(List.of());
        given(shopImageRepository.save(any(ShopImage.class)))
                .willThrow(new RuntimeException("강제 DB 실패"));

        assertThatThrownBy(() ->
                shopImageService.uploadImage(USER_ID, shop.getId(), ShopImageType.GALLERY, jpeg()))
                .isInstanceOf(RuntimeException.class);

        // 롤백 동기화(afterCompletion ROLLED_BACK)가 방금 올린 객체를 회수해야 한다
        verify(imageStorage).delete(uploadedKey);
    }

    @Test
    @DisplayName("업로드 커밋 성공 → 보상 훅이 객체를 삭제하지 않음(커밋 경로 회귀 가드)")
    void uploadCommit_doesNotDeleteObject() {
        Shop shop = shopRepository.save(Shop.builder()
                .userId(USER_ID).applicationId(USER_ID)
                .shopName("커밋성공").category("FOOD")
                .address("서울시 강남구").build());

        String uploadedKey = "shops/" + shop.getId() + "/keep.jpg";
        given(imageStorage.upload(anyLong(), any())).willReturn(uploadedKey);
        given(imageStorage.presignedGetUrl(uploadedKey)).willReturn(uploadedKey);
        given(shopImageRepository.findByShopIdAndTypeForUpdate(shop.getId(), ShopImageType.GALLERY))
                .willReturn(List.of());
        // save 정상 → 트랜잭션 커밋. 보상 훅은 ROLLED_BACK에만 동작하므로 삭제가 일어나면 안 된다.
        given(shopImageRepository.save(any(ShopImage.class))).willAnswer(inv -> inv.getArgument(0));

        shopImageService.uploadImage(USER_ID, shop.getId(), ShopImageType.GALLERY, jpeg());

        verify(imageStorage, never()).delete(uploadedKey);
    }
}
