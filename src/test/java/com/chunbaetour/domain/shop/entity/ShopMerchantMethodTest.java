package com.chunbaetour.domain.shop.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.ShopStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Shop.merchantChangeStatus() 도메인 단위 테스트 (KAN-213).
 */
class ShopMerchantMethodTest {

    private static Shop newShop() {
        return Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("가게").category("FOOD")
                .address("서울시 강남구").build();
    }

    @Nested
    @DisplayName("merchantChangeStatus()")
    class MerchantChangeStatus {

        @Test
        @DisplayName("ACTIVE → CLOSED 전환 성공")
        void active_to_closed() {
            Shop shop = newShop();

            shop.merchantChangeStatus(ShopStatus.CLOSED);

            assertThat(shop.getStatus()).isEqualTo(ShopStatus.CLOSED);
        }

        @Test
        @DisplayName("CLOSED → ACTIVE 재개 성공")
        void closed_to_active() {
            Shop shop = newShop();
            ReflectionTestUtils.setField(shop, "status", ShopStatus.CLOSED);

            shop.merchantChangeStatus(ShopStatus.ACTIVE);

            assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE → ACTIVE 멱등(no-op)")
        void active_to_active_idempotent() {
            Shop shop = newShop();

            shop.merchantChangeStatus(ShopStatus.ACTIVE);

            assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
        }

        @Test
        @DisplayName("SUSPENDED 전환 시 SHOP_STATUS_FORBIDDEN")
        void suspended_request_throws() {
            Shop shop = newShop();

            assertThatThrownBy(() -> shop.merchantChangeStatus(ShopStatus.SUSPENDED))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_STATUS_FORBIDDEN);
        }

        @Test
        @DisplayName("현재 SUSPENDED 상태에서 변경 시 SHOP_INACTIVE")
        void from_suspended_throws_shop_inactive() {
            Shop shop = newShop();
            shop.hide(); // ACTIVE → SUSPENDED

            assertThatThrownBy(() -> shop.merchantChangeStatus(ShopStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_INACTIVE);
        }
    }
}
