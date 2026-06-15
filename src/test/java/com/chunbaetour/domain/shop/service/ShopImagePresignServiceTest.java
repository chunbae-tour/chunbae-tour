package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * ShopService의 imageUrls presigned 변환 + 키 소유권(IDOR) 검증 (E10 PR3).
 * 실제 ObjectMapper(JSON 파싱)를 써야 검증되므로 ShopServiceTest(objectMapper mock)와 분리한 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ShopImagePresignServiceTest {

    @Mock private ShopRepository shopRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private ShopWalletRepository shopWalletRepository;
    @Mock private com.chunbaetour.domain.place.repository.PlaceRepository placeRepository;
    @Mock private com.chunbaetour.domain.market.repository.TraditionalMarketRepository traditionalMarketRepository;
    @Mock private ShopImageStorage imageStorage;

    private ShopService shopService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    @BeforeEach
    void setUp() {
        // 실제 ObjectMapper — readTree/writeValueAsString 필요(presign·검증).
        shopService = new ShopService(shopRepository, menuRepository, shopWalletRepository,
                new ObjectMapper(), placeRepository, traditionalMarketRepository, imageStorage);
    }

    private Shop activeShop() {
        Shop shop = Shop.builder()
                .userId(USER_ID).applicationId(1L).shopName("가게").category("FOOD")
                .address("서울").phone("02-1234-5678").description("").build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    @Test
    @DisplayName("조회 — 저장된 자기 가게 키가 presigned URL JSON 배열로 변환됨")
    void getMyShop_presignsOwnKeys() {
        Shop shop = activeShop();
        ReflectionTestUtils.setField(shop, "imageUrls", "[\"shops/10/a.jpg\",\"shops/10/b.png\"]");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(imageStorage.presignedGetUrl(anyString())).willAnswer(inv -> "https://signed/" + inv.getArgument(0));

        ShopResponse response = shopService.getMyShop(USER_ID, SHOP_ID);

        assertThat(response.imageUrls())
                .isEqualTo("[\"https://signed/shops/10/a.jpg\",\"https://signed/shops/10/b.png\"]");
    }

    @Test
    @DisplayName("조회 — 타 가게 prefix 키는 presign에서 제외(2중 방어)")
    void getMyShop_skipsForeignKeys() {
        Shop shop = activeShop();
        ReflectionTestUtils.setField(shop, "imageUrls", "[\"shops/10/own.jpg\",\"shops/999/foreign.jpg\"]");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(imageStorage.presignedGetUrl(anyString())).willAnswer(inv -> "https://signed/" + inv.getArgument(0));

        ShopResponse response = shopService.getMyShop(USER_ID, SHOP_ID);

        // 자기 키만 presign, 타 가게 키는 빠짐
        assertThat(response.imageUrls()).isEqualTo("[\"https://signed/shops/10/own.jpg\"]");
    }

    @Test
    @DisplayName("수정 — imageUrls에 타 가게/임의 키 저장 시도 → INVALID_REQUEST (IDOR 차단)")
    void updateMyShop_foreignKey_rejected() {
        Shop shop = activeShop();
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        ShopUpdateRequest request = new ShopUpdateRequest(
                null, null, null, null, null, null, "[\"shops/999/foreign.jpg\"]");

        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, SHOP_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("수정 — imageUrls에 자기 가게 키만 있으면 통과")
    void updateMyShop_ownKey_passes() {
        Shop shop = activeShop();
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(imageStorage.presignedGetUrl(anyString())).willAnswer(inv -> "https://signed/" + inv.getArgument(0));
        ShopUpdateRequest request = new ShopUpdateRequest(
                null, null, null, null, null, null, "[\"shops/10/ok.jpg\"]");

        ShopResponse response = shopService.updateMyShop(USER_ID, SHOP_ID, request);

        assertThat(response.imageUrls()).isEqualTo("[\"https://signed/shops/10/ok.jpg\"]");
    }

    @Test
    @DisplayName("조회 — presign 실패는 해당 이미지만 제외하고 조회 성공(전체 503 아님, graceful degrade)")
    void getMyShop_presignFailure_degradesNot503() {
        Shop shop = activeShop();
        ReflectionTestUtils.setField(shop, "imageUrls", "[\"shops/10/a.jpg\",\"shops/10/b.jpg\"]");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        // a.jpg는 성공, b.jpg는 S3 장애로 실패 → b만 빠지고 조회는 성공해야 함
        given(imageStorage.presignedGetUrl("shops/10/a.jpg")).willReturn("https://signed/a");
        given(imageStorage.presignedGetUrl("shops/10/b.jpg"))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        ShopResponse response = shopService.getMyShop(USER_ID, SHOP_ID);

        assertThat(response.imageUrls()).isEqualTo("[\"https://signed/a\"]"); // b 제외, 예외 전파 안 됨
    }

    @Test
    @DisplayName("조회 — imageUrls 빈 배열 → 빈 배열 반환")
    void getMyShop_emptyArray() {
        Shop shop = activeShop();
        ReflectionTestUtils.setField(shop, "imageUrls", "[]");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        ShopResponse response = shopService.getMyShop(USER_ID, SHOP_ID);

        assertThat(response.imageUrls()).isEqualTo("[]");
    }

    @Test
    @DisplayName("목록 조회 — 여러 가게 각각 자기 키 presign")
    void getMyShops_presignsEachShop() {
        Shop shop = activeShop();
        ReflectionTestUtils.setField(shop, "imageUrls", "[\"shops/10/a.jpg\"]");
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(java.util.List.of(shop));
        given(imageStorage.presignedGetUrl(anyString())).willAnswer(inv -> "https://signed/" + inv.getArgument(0));

        var responses = shopService.getMyShops(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).imageUrls()).isEqualTo("[\"https://signed/shops/10/a.jpg\"]");
    }
}
