package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.shop.dto.response.AdminShopMarketResponse;
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
 * ShopService.updateShopMarket() 단위 테스트 (KAN-268, 가게-전통시장 수동 연결).
 */
@ExtendWith(MockitoExtension.class)
class ShopMarketServiceTest {

    @Mock ShopRepository shopRepository;
    @Mock MenuRepository menuRepository;
    @Mock ShopWalletRepository shopWalletRepository;
    @Mock ObjectMapper objectMapper;
    @Mock PlaceRepository placeRepository;
    @Mock TraditionalMarketRepository traditionalMarketRepository;

    @InjectMocks ShopService shopService;

    private static final Long SHOP_ID = 10L;
    private static final Long MARKET_ID = 200L;

    private Shop createShop() {
        Shop shop = Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("테스트 가게").category("FOOD")
                .address("서울시 종로구").build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    private TraditionalMarket marketMock(String name) {
        TraditionalMarket market = mock(TraditionalMarket.class);
        given(market.getName()).willReturn(name);
        return market;
    }

    @Nested
    @DisplayName("updateShopMarket()")
    class UpdateShopMarket {

        @Test
        @DisplayName("전통시장 연결 성공 — 연결 결과 DTO 반환")
        void linkMarket_success() {
            Shop shop = createShop();
            TraditionalMarket market = marketMock("광장시장");
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(traditionalMarketRepository.findById(MARKET_ID)).willReturn(Optional.of(market));

            AdminShopMarketResponse res = shopService.updateShopMarket(SHOP_ID, MARKET_ID);

            assertThat(shop.getTraditionalMarketId()).isEqualTo(MARKET_ID);
            assertThat(res.shopId()).isEqualTo(SHOP_ID);
            assertThat(res.traditionalMarketId()).isEqualTo(MARKET_ID);
            assertThat(res.marketName()).isEqualTo("광장시장");
            assertThat(res.linked()).isTrue();
        }

        @Test
        @DisplayName("traditionalMarketId=null — 연결 해제, 해제 결과 DTO 반환")
        void unlinkMarket_success() {
            Shop shop = createShop();
            ReflectionTestUtils.setField(shop, "traditionalMarketId", MARKET_ID);
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

            AdminShopMarketResponse res = shopService.updateShopMarket(SHOP_ID, null);

            assertThat(shop.getTraditionalMarketId()).isNull();
            assertThat(res.shopId()).isEqualTo(SHOP_ID);
            assertThat(res.traditionalMarketId()).isNull();
            assertThat(res.marketName()).isNull();
            assertThat(res.linked()).isFalse();
        }

        @Test
        @DisplayName("기존 연결 가게를 다른 전통시장으로 변경 — id 교체 + 새 결과 DTO")
        void relinkMarket_success() {
            Long newMarketId = 300L;
            Shop shop = createShop();
            ReflectionTestUtils.setField(shop, "traditionalMarketId", MARKET_ID); // 기존 연결 존재
            TraditionalMarket market = marketMock("새 시장");
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
            given(traditionalMarketRepository.findById(newMarketId)).willReturn(Optional.of(market));

            AdminShopMarketResponse res = shopService.updateShopMarket(SHOP_ID, newMarketId);

            assertThat(shop.getTraditionalMarketId()).isEqualTo(newMarketId);
            assertThat(res.traditionalMarketId()).isEqualTo(newMarketId);
            assertThat(res.marketName()).isEqualTo("새 시장");
            assertThat(res.linked()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 전통시장 — MARKET_NOT_FOUND")
        void linkMarket_marketNotFound() {
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(createShop()));
            given(traditionalMarketRepository.findById(MARKET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateShopMarket(SHOP_ID, MARKET_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MARKET_NOT_FOUND);
        }

        @Test
        @DisplayName("가게 없음 — SHOP_NOT_FOUND")
        void linkMarket_shopNotFound() {
            given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateShopMarket(SHOP_ID, MARKET_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        }
    }
}
