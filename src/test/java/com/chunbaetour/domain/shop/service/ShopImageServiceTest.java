package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ShopImageServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private ShopImageService shopImageService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID).applicationId(1L).shopName("테스트 가게")
                .category("FOOD").address("서울").phone("02-1234-5678").description("").build();
    }

    private MockMultipartFile validFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[1024]);
    }

    @Test
    @DisplayName("이미지 업로드 — 타인 가게 → SHOP_NOT_FOUND")
    void uploadImage_notOwner_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, validFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("이미지 업로드 — 5MB 초과 → INVALID_REQUEST")
    void uploadImage_fileTooLarge_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, largeFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미지 업로드 — 허용되지 않는 파일 타입 → INVALID_REQUEST")
    void uploadImage_invalidContentType_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile gifFile = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[1024]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, gifFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미지 업로드 — 빈 파일 → INVALID_REQUEST")
    void uploadImage_emptyFile_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, emptyFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미지 업로드 — 유효한 파일이나 S3 미설정 → EXTERNAL_SERVICE_ERROR (stub)")
    void uploadImage_validFile_s3NotConfigured_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, validFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
