package com.chunbaetour.domain.admin.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.admin.shop.dto.request.AdminShopUpdateRequest;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopDetailResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AdminShopService 단위 테스트 (KAN-203 Admin S04).
 *
 * <p>status 분기(activate/hide), CLOSED 거부, partial update, 미존재 404, 카운트 위임 검증.
 */
@ExtendWith(MockitoExtension.class)
class AdminShopServiceTest {

    private static final Long SHOP_ID = 10L;

    @Mock private ShopRepository shopRepository;
    @InjectMocks private AdminShopService adminShopService;

    private static Shop shopWithStatus(ShopStatus status) {
        Shop shop = Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("가게").category("FOOD")
                .address("서울시 강남구").phone("02-1111-2222")
                .description("기존 소개").build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        ReflectionTestUtils.setField(shop, "status", status);
        return shop;
    }

    @Test
    @DisplayName("getShop — 미존재 시 SHOP_NOT_FOUND")
    void getShop_notFound() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminShopService.getShop(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("updateShop status=SUSPENDED → hide() 전이 + 필드 partial 반영")
    void updateShop_suspend() {
        Shop shop = shopWithStatus(ShopStatus.ACTIVE);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        AdminShopDetailResponse res = adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.SUSPENDED, "수정된 소개", null, null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(shop.getDescription()).isEqualTo("수정된 소개");
        assertThat(res.status()).isEqualTo(ShopStatus.SUSPENDED);
    }

    @Test
    @DisplayName("updateShop status=ACTIVE → activate() 복구")
    void updateShop_activate() {
        Shop shop = shopWithStatus(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.ACTIVE, null, null, null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateShop status=CLOSED 직접 지정 → INVALID_INPUT_VALUE")
    void updateShop_closed_rejected() {
        Shop shop = shopWithStatus(ShopStatus.ACTIVE);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.CLOSED, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("updateShop CLOSED 가게 status 전이 → SHOP_INACTIVE (activate/hide 가드)")
    void updateShop_closedShop_guard() {
        Shop shop = shopWithStatus(ShopStatus.CLOSED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.ACTIVE, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("updateShop status=null → 상태 불변, 필드만 partial")
    void updateShop_noStatus() {
        Shop shop = shopWithStatus(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(null, "정지 중 소개 수정", "02-9999-9999", null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(shop.getDescription()).isEqualTo("정지 중 소개 수정");
        assertThat(shop.getPhone()).isEqualTo("02-9999-9999");
    }

    @Test
    @DisplayName("getTotalShops / getSuspendedShops — repository 위임")
    void countMethods_delegate() {
        given(shopRepository.count()).willReturn(42L);
        given(shopRepository.countByStatus(ShopStatus.SUSPENDED)).willReturn(7L);

        assertThat(adminShopService.getTotalShops()).isEqualTo(42L);
        assertThat(adminShopService.getSuspendedShops()).isEqualTo(7L);
    }
}
