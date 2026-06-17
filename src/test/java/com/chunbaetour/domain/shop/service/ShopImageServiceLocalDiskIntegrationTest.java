package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.shop.dto.response.ShopImageItemResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.LocalDiskShopImageStorage;
import com.chunbaetour.domain.shop.type.ShopImageType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ShopImageService → 실제 LocalDiskShopImageStorage end-to-end (mock 아님).
 *
 * <p>validateFile()이 magic-byte 검사로 {@code getInputStream().readNBytes(12)}를 한 번 읽고,
 * 이후 storage.upload()가 같은 MultipartFile의 getInputStream()을 다시 읽는다. MultipartFile이
 * 호출마다 처음부터인 새 스트림을 준다는 가정이 깨지면 "앞 12바이트(=magic byte) 잘림 → 전 이미지 손상"이라는
 * 치명적 회귀가 생기므로, 저장된 파일이 원본과 바이트 단위로 동일한지 검증한다(lim-haeun·hyeonmin02 리뷰).
 *
 * <p>KAN-315: 업로드 1단계화로 행이 생성되므로 {@code ShopImageRepository}는 mock으로 두고(영속화는 JPA 통합
 * 테스트가 검증), 본 테스트는 디스크 저장 바이트 보존에 집중한다. 로컬 storage는 presign passthrough라 url=객체키.
 */
@ExtendWith(MockitoExtension.class)
class ShopImageServiceLocalDiskIntegrationTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ShopImageRepository shopImageRepository;

    @TempDir
    Path tempDir;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    private Shop createShop() {
        Shop shop = Shop.builder()
                .userId(USER_ID).applicationId(1L).shopName("테스트 가게")
                .category("FOOD").address("서울").phone("02-1234-5678").description("").build();
        // 영속 전 엔티티라 id=null — 서비스가 shop.getId()로 행을 생성하므로 SHOP_ID를 주입(stub 인자 정합)
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    @Test
    @DisplayName("LocalDisk e2e — 검증(12바이트 read) 후 업로드해도 저장 파일이 원본과 바이트 동일(magic byte 보존)")
    void uploadImage_localDisk_preservesAllBytes() throws Exception {
        ShopImageService service = new ShopImageService(
                shopRepository, shopImageRepository, new LocalDiskShopImageStorage(tempDir.toString()));
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        // GALLERY append — Shop 행 FOR UPDATE 잠금(직렬화) 후 기존 갤러리 max+1 계산
        given(shopRepository.findByIdAndUserIdWithLock(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        // 기존 갤러리 없음(sort_order=0), save는 전달된 엔티티 그대로 반환
        given(shopImageRepository.findByShopIdAndTypeForUpdate(SHOP_ID, ShopImageType.GALLERY)).willReturn(List.of());
        given(shopImageRepository.save(any(ShopImage.class))).willAnswer(inv -> inv.getArgument(0));

        // JPEG 매직바이트(FF D8 FF) + 식별용 패턴 바이트 — 잘림 시 앞부분이 사라져 비교 실패한다.
        byte[] original = new byte[64];
        original[0] = (byte) 0xFF;
        original[1] = (byte) 0xD8;
        original[2] = (byte) 0xFF;
        for (int i = 3; i < original.length; i++) {
            original[i] = (byte) i;
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", original);

        ShopImageItemResponse response = service.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY, file);

        // 로컬 storage는 presign passthrough라 url = 객체키
        assertThat(response.url()).matches("shops/10/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(response.type()).isEqualTo(ShopImageType.GALLERY);
        Path stored = tempDir.resolve(response.url());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(original); // 첫 바이트부터 손실 없이 보존
    }
}
