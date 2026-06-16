package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.shop.dto.response.ShopImageResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.LocalDiskShopImageStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * ShopImageService → 실제 LocalDiskShopImageStorage end-to-end (mock 아님).
 *
 * <p>validateFile()이 magic-byte 검사로 {@code getInputStream().readNBytes(12)}를 한 번 읽고,
 * 이후 storage.upload()가 같은 MultipartFile의 getInputStream()을 다시 읽는다. MultipartFile이
 * 호출마다 처음부터인 새 스트림을 준다는 가정이 깨지면 "앞 12바이트(=magic byte) 잘림 → 전 이미지 손상"이라는
 * 치명적 회귀가 생기므로, 저장된 파일이 원본과 바이트 단위로 동일한지 검증한다(lim-haeun·hyeonmin02 리뷰).
 */
@ExtendWith(MockitoExtension.class)
class ShopImageServiceLocalDiskIntegrationTest {

    @Mock
    private ShopRepository shopRepository;

    @TempDir
    Path tempDir;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID).applicationId(1L).shopName("테스트 가게")
                .category("FOOD").address("서울").phone("02-1234-5678").description("").build();
    }

    @Test
    @DisplayName("LocalDisk e2e — 검증(12바이트 read) 후 업로드해도 저장 파일이 원본과 바이트 동일(magic byte 보존)")
    void uploadImage_localDisk_preservesAllBytes() throws Exception {
        ShopImageService service =
                new ShopImageService(shopRepository, new LocalDiskShopImageStorage(tempDir.toString()));
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));

        // JPEG 매직바이트(FF D8 FF) + 식별용 패턴 바이트 — 잘림 시 앞부분이 사라져 비교 실패한다.
        byte[] original = new byte[64];
        original[0] = (byte) 0xFF;
        original[1] = (byte) 0xD8;
        original[2] = (byte) 0xFF;
        for (int i = 3; i < original.length; i++) {
            original[i] = (byte) i;
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", original);

        ShopImageResponse response = service.uploadImage(USER_ID, SHOP_ID, file);

        assertThat(response.objectKey()).matches("shops/10/[0-9a-fA-F\\-]{36}\\.jpg");
        Path stored = tempDir.resolve(response.objectKey());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(original); // 첫 바이트부터 손실 없이 보존
    }
}
