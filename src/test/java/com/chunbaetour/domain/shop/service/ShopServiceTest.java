package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private Shop shop;

    @InjectMocks
    private ShopService shopService;

    private static final Long USER_ID = 1L;

    // ── GET /merchants/me/shop ──────────────────────────────────────────────

    @Test
    @DisplayName("내 가게 조회 — 성공")
    void getMyShop_success() {
        // given — shopRepository가 shop 반환하도록 stub
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        stubShopGetters();

        // when
        ShopResponse response = shopService.getMyShop(USER_ID);

        // then
        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 가게 조회 — 가게 없음 → SHOP_NOT_FOUND")
    void getMyShop_notFound_throws() {
        // given — userId에 해당하는 가게 없음
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> shopService.getMyShop(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── PATCH /merchants/me/shop ────────────────────────────────────────────

    @Test
    @DisplayName("내 가게 수정 — 성공 (부분 수정)")
    void updateMyShop_success() {
        // given — 일부 필드만 수정 (null 필드는 기존 값 유지)
        ShopUpdateRequest request = new ShopUpdateRequest(
                "새로운 가게명", null, "02-9999-8888", "업데이트된 소개글", null, null, null
        );
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getUserId()).willReturn(USER_ID);
        stubShopGetters();

        // when
        shopService.updateMyShop(USER_ID, request);

        // then — shop.update() 호출 확인
        verify(shop).update(request);
    }

    @Test
    @DisplayName("내 가게 수정 — 가게 없음 → SHOP_NOT_FOUND")
    void updateMyShop_notFound_throws() {
        // given — userId에 해당하는 가게 없음
        ShopUpdateRequest request = new ShopUpdateRequest(null, null, null, null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── 공통 stub ──────────────────────────────────────────────────────────

    /** ShopResponse.from(shop) 호출 시 필요한 getter stub */
    private void stubShopGetters() {
        given(shop.getId()).willReturn(1L);
        given(shop.getUserId()).willReturn(USER_ID);
        given(shop.getShopName()).willReturn("광화문 떡볶이");
        given(shop.getCategory()).willReturn("FOOD");
        given(shop.getAddress()).willReturn("서울 종로구 세종대로 172");
        given(shop.getLat()).willReturn(null);
        given(shop.getLng()).willReturn(null);
        given(shop.getPhone()).willReturn("02-1234-5678");
        given(shop.getDescription()).willReturn("전통 떡볶이 전문점");
        given(shop.getImageUrls()).willReturn(null);
        given(shop.getOperatingHours()).willReturn("10:00~21:00");
        given(shop.getClosedDays()).willReturn("매주 일요일");
        given(shop.isCertified()).willReturn(false);
        given(shop.getRating()).willReturn(4.5f);
        given(shop.getReviewCount()).willReturn(10);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
    }
}
