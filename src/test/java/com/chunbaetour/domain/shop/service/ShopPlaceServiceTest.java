package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.shop.dto.response.AdminShopPlaceResponse;
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
 * ShopService.updateShopPlace() 단위 테스트 (KAN-217, 응답 DTO 보강 KAN-254).
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

    /** 연결 검증·응답에 쓰는 Place mock (status/name만 stub). */
    private Place placeMock(PlaceStatus status, String name) {
        Place place = mock(Place.class);
        given(place.getStatus()).willReturn(status);
        if (name != null) {
            given(place.getName()).willReturn(name);
        }
        return place;
    }

    @Nested
    @DisplayName("updateShopPlace()")
    class UpdateShopPlace {

        @Test
        @DisplayName("placeId 연결 성공 — 연결 결과 DTO 반환")
        void linkPlace_success() {
            Shop shop = createShop();
            Place place = placeMock(PlaceStatus.ACTIVE, "천안삼거리공원");
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(placeRepository.findById(PLACE_ID)).willReturn(Optional.of(place));

            AdminShopPlaceResponse res = shopService.updateShopPlace(SHOP_ID, PLACE_ID);

            assertThat(shop.getPlaceId()).isEqualTo(PLACE_ID);
            assertThat(res.shopId()).isEqualTo(SHOP_ID);
            assertThat(res.placeId()).isEqualTo(PLACE_ID);
            assertThat(res.placeName()).isEqualTo("천안삼거리공원");
            assertThat(res.linked()).isTrue();
        }

        @Test
        @DisplayName("placeId=null — 연결 해제, 해제 결과 DTO 반환")
        void unlinkPlace_success() {
            Shop shop = createShop();
            ReflectionTestUtils.setField(shop, "placeId", PLACE_ID);
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

            AdminShopPlaceResponse res = shopService.updateShopPlace(SHOP_ID, null);

            assertThat(shop.getPlaceId()).isNull();
            assertThat(res.shopId()).isEqualTo(SHOP_ID);
            assertThat(res.placeId()).isNull();
            assertThat(res.placeName()).isNull();
            assertThat(res.linked()).isFalse();
        }

        @Test
        @DisplayName("기존 연결 가게를 다른 Place로 변경 — placeId 교체 + 새 결과 DTO")
        void relinkPlace_success() {
            Long newPlaceId = 200L;
            Shop shop = createShop();
            ReflectionTestUtils.setField(shop, "placeId", PLACE_ID); // 기존 연결 존재
            Place place = placeMock(PlaceStatus.ACTIVE, "새 장소");
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(placeRepository.findById(newPlaceId)).willReturn(Optional.of(place));

            AdminShopPlaceResponse res = shopService.updateShopPlace(SHOP_ID, newPlaceId);

            assertThat(shop.getPlaceId()).isEqualTo(newPlaceId);
            assertThat(res.placeId()).isEqualTo(newPlaceId);
            assertThat(res.placeName()).isEqualTo("새 장소");
            assertThat(res.linked()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 Place — PLACE_NOT_FOUND")
        void linkPlace_placeNotFound() {
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(createShop()));
            given(placeRepository.findById(PLACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateShopPlace(SHOP_ID, PLACE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
        }

        @Test
        @DisplayName("DELETED 상태 Place — PLACE_NOT_FOUND (soft delete 장소 연결 차단)")
        void linkPlace_deletedPlace() {
            Place deleted = placeMock(PlaceStatus.DELETED, null);
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(createShop()));
            given(placeRepository.findById(PLACE_ID)).willReturn(Optional.of(deleted));

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
