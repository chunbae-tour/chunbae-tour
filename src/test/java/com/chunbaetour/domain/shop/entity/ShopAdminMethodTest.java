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
 * Shop.activate() / Shop.adminUpdate() 도메인 단위 테스트 (KAN-203 Admin S04).
 *
 * <p>activate() 상태 가드(hide()와 대칭) + adminUpdate() null-skip partial 검증.
 */
class ShopAdminMethodTest {

    private static Shop newShop() {
        return Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("가게").category("FOOD")
                .address("서울시 강남구").phone("02-1111-2222")
                .description("기존 소개").build();
    }

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        @DisplayName("SUSPENDED → ACTIVE 복구")
        void suspended_to_active() {
            Shop shop = newShop();
            shop.hide(); // ACTIVE → SUSPENDED
            assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);

            shop.activate();

            assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
        }

        @Test
        @DisplayName("이미 ACTIVE면 멱등(no-op)")
        void already_active_is_idempotent() {
            Shop shop = newShop();

            shop.activate();

            assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
        }

        @Test
        @DisplayName("CLOSED 가게 복구 시 SHOP_INACTIVE (hide()와 대칭 예외)")
        void closed_throws_shop_inactive() {
            Shop shop = newShop();
            ReflectionTestUtils.setField(shop, "status", ShopStatus.CLOSED);

            assertThatThrownBy(shop::activate)
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_INACTIVE);
        }
    }

    @Nested
    @DisplayName("adminUpdate()")
    class AdminUpdate {

        @Test
        @DisplayName("non-null 필드만 반영, null은 미수정")
        void null_skip_partial() {
            Shop shop = newShop();

            shop.adminUpdate("새 소개", null, "월~금 09:00-18:00");

            assertThat(shop.getDescription()).isEqualTo("새 소개");
            assertThat(shop.getPhone()).isEqualTo("02-1111-2222"); // null → 미수정
            assertThat(shop.getOperatingHours()).isEqualTo("월~금 09:00-18:00");
        }

        @Test
        @DisplayName("전부 null이면 아무 것도 안 바뀜")
        void all_null_no_change() {
            Shop shop = newShop();

            shop.adminUpdate(null, null, null);

            assertThat(shop.getDescription()).isEqualTo("기존 소개");
            assertThat(shop.getPhone()).isEqualTo("02-1111-2222");
        }

        @Test
        @DisplayName("SUSPENDED 가게도 수정 가능 (상인용 update()의 ACTIVE 가드 없음)")
        void works_on_suspended() {
            Shop shop = newShop();
            shop.hide(); // SUSPENDED

            shop.adminUpdate("정지 중 수정", null, null);

            assertThat(shop.getDescription()).isEqualTo("정지 중 수정");
            assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED); // 상태는 안 건드림
        }
    }
}
