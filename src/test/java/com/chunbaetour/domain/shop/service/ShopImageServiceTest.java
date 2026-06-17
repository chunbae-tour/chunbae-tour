package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import com.chunbaetour.domain.shop.type.ShopImageType;
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

    @Mock
    private ShopImageStorage imageStorage;

    @Mock
    private ShopImageRepository shopImageRepository;

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
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(1024));
    }

    /** JPEG 매직바이트(FF D8 FF)로 시작하는 더미 — magic-byte 검증(E10) 통과용. */
    private static byte[] jpegBytes(int len) {
        byte[] b = new byte[len];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }

    @Test
    @DisplayName("이미지 업로드 — 타인 가게 → SHOP_NOT_FOUND")
    void uploadImage_notOwner_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,validFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("이미지 업로드 — 5MB 초과 → SHOP_IMAGE_FILE_TOO_LARGE")
    void uploadImage_fileTooLarge_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,largeFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_IMAGE_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("이미지 업로드 — 허용되지 않는 파일 타입 → SHOP_IMAGE_TYPE_UNSUPPORTED")
    void uploadImage_invalidContentType_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile gifFile = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[1024]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,gifFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("이미지 업로드 — 빈 파일 → SHOP_IMAGE_FILE_EMPTY")
    void uploadImage_emptyFile_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,emptyFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_IMAGE_FILE_EMPTY);
    }

    @Test
    @DisplayName("계약 검증: SHOP_IMAGE_FILE_EMPTY 응답 code 값이 SHOP_024 (KAN-361 에러코드 중복 수정)")
    void shopImageFileEmpty_errorCode_isShop024() {
        // SHOP_016 → SHOP_024 변경 후 클라이언트 노출 code 문자열 계약 검증
        assertThat(ErrorCode.SHOP_IMAGE_FILE_EMPTY.getCode()).isEqualTo("SHOP_024");
    }

    @Test
    @DisplayName("이미지 업로드 — 검증 통과 후 storage 위임 → 키 반환, storage 오류는 그대로 전파")
    void uploadImage_storageError_propagates() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        given(imageStorage.upload(eq(SHOP_ID), any()))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,validFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("이미지 업로드 — declared는 image/jpeg지만 매직바이트가 이미지 아님(위장) → SHOP_IMAGE_TYPE_UNSUPPORTED")
    void uploadImage_magicByteMismatch_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        // content-type은 image/jpeg로 위장했지만 실제 바이트는 'MZ'(exe) 시그니처 → magic-byte 검증서 거부
        byte[] fake = new byte[1024];
        fake[0] = 'M';
        fake[1] = 'Z';
        MockMultipartFile disguised = new MockMultipartFile("file", "evil.jpg", "image/jpeg", fake);

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,disguised))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("이미지 업로드 — 비활성(SUSPENDED) 가게 → SHOP_INACTIVE (고아 객체 사전 차단)")
    void uploadImage_inactiveShop_throws() {
        com.chunbaetour.domain.shop.entity.Shop suspended = createShop();
        org.springframework.test.util.ReflectionTestUtils.setField(
                suspended, "status", com.chunbaetour.domain.shop.type.ShopStatus.SUSPENDED);
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(suspended));

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,validFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("이미지 업로드 — declared image/png 인데 실제 바이트는 JPEG(포맷 불일치) → SHOP_IMAGE_TYPE_UNSUPPORTED")
    void uploadImage_declaredPngButJpegBytes_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        // declared=image/png 이지만 실제 시그니처는 JPEG(FF D8 FF) → declared↔magic 매칭 검증서 거부(hyeonmin02 리뷰)
        MockMultipartFile mismatch = new MockMultipartFile("file", "x.png", "image/png", jpegBytes(1024));

        assertThatThrownBy(() -> shopImageService.uploadImage(USER_ID, SHOP_ID, ShopImageType.GALLERY,mismatch))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
    }
}
