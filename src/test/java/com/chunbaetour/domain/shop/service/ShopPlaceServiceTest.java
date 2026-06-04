package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ShopService.updateShopPlace() 단위 테스트 (KAN-217).
 */
@ExtendWith(MockitoExtension.class)
class ShopPlaceServiceTest {

    @Mock ShopRepository shopRepository;
    @Mock MenuRepository menuRepository;
    @Mock ShopWalletRepository shopWalletRepository;
    @Mock ObjectMapper objectMapper;
    @Mock PlaceRepository placeRepository;

    @InjectMocks ShopService shopService;

    private static final Long SHOP_ID = 10L;
    private static final Long PLACE_ID = 100L;

    private Shop createShop() {
        Shop shop = Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("테스트 가게").category("FOOD")
                .address("서울시 종로구").build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    @Nested
    @DisplayName("updateShopPlace()")
    class UpdateShopPlace {

        @Test
        @DisplayName("placeId 연결 성공")
        void linkPlace_success() {
            Shop shop = createShop();
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(placeRepository.existsByIdAndStatusNot(PLACE_ID, PlaceStatus.DELETED)).willReturn(true);

            shopService.updateShopPlace(SHOP_ID, PLACE_ID);

            assertThat(shop.getPlaceId()).isEqualTo(PLACE_ID);
        }

        @Test
        @DisplayName("placeId=null — 연결 해제")
        void unlinkPlace_success() {
            Shop shop = createShop();
            ReflectionTestUtils.setField(shop, "placeId", PLACE_ID);
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

            shopService.updateShopPlace(SHOP_ID, null);

            assertThat(shop.getPlaceId()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 Place — PLACE_NOT_FOUND")
        void linkPlace_placeNotFound() {
            Shop shop = createShop();
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(placeRepository.existsByIdAndStatusNot(PLACE_ID, PlaceStatus.DELETED)).willReturn(false);

            assertThatThrownBy(() -> shopService.updateShopPlace(SHOP_ID, PLACE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
        }

        @Test
        @DisplayName("DELETED 상태 Place — PLACE_NOT_FOUND (soft delete 장소 연결 차단)")
        void linkPlace_deletedPlace() {
            Shop shop = createShop();
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            // existsByIdAndStatusNot(DELETED)는 DELETED 장소를 false로 반환
            given(placeRepository.existsByIdAndStatusNot(PLACE_ID, PlaceStatus.DELETED)).willReturn(false);

            assertThatThrownBy(() -> shopService.updateShopPlace(SHOP_ID, PLACE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
        }

        @Test
        @DisplayName("가게 없음 — SHOP_NOT_FOUND")
        void linkPlace_shopNotFound() {
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateShopPlace(SHOP_ID, PLACE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        }
    }
}
