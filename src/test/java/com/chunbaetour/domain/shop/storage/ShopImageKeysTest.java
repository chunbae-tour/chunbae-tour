package com.chunbaetour.domain.shop.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShopImageKeysTest {

    @Test
    @DisplayName("objectKey — shops/{shopId}/{uuid}.{ext} 형식, content-type별 확장자")
    void objectKey_format() {
        assertThat(ShopImageKeys.objectKey(10L, "image/jpeg")).matches("shops/10/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(ShopImageKeys.objectKey(10L, "image/png")).matches("shops/10/[0-9a-fA-F\\-]{36}\\.png");
        assertThat(ShopImageKeys.objectKey(10L, "image/webp")).matches("shops/10/[0-9a-fA-F\\-]{36}\\.webp");
    }

    @Test
    @DisplayName("objectKey — 허용되지 않은 content-type → SHOP_IMAGE_TYPE_UNSUPPORTED")
    void objectKey_unsupported_throws() {
        assertThatThrownBy(() -> ShopImageKeys.objectKey(10L, "image/gif"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ShopImageKeys.objectKey(10L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("belongsToShop — 자기 가게 prefix만 true (IDOR 방지)")
    void belongsToShop() {
        assertThat(ShopImageKeys.belongsToShop("shops/10/uuid.jpg", 10L)).isTrue();
        assertThat(ShopImageKeys.belongsToShop("shops/999/uuid.jpg", 10L)).isFalse(); // 타 가게
        assertThat(ShopImageKeys.belongsToShop("arbitrary-key", 10L)).isFalse();      // 임의 객체
        assertThat(ShopImageKeys.belongsToShop("shops/100/uuid.jpg", 10L)).isFalse(); // prefix 부분일치 방지
        assertThat(ShopImageKeys.belongsToShop(null, 10L)).isFalse();
        assertThat(ShopImageKeys.belongsToShop("shops/null/uuid.jpg", null)).isFalse(); // shopId null 오인 매칭 차단
    }
}
