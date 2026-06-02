package com.chunbaetour.domain.shop.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Shop#markCertified()} / {@link Shop#unmarkCertified()} 토글 단위 테스트 (KAN-204 Admin S05).
 */
class ShopCertificationToggleTest {

    private Shop shop() {
        return Shop.builder()
                .userId(1L)
                .applicationId(1L)
                .shopName("테스트가게")
                .category("음식점")
                .address("춘천시")
                .lat(BigDecimal.valueOf(37.0))
                .lng(BigDecimal.valueOf(127.0))
                .description("설명")
                .build();
    }

    @Test
    @DisplayName("markCertified() → isCertified true")
    void markCertified_sets_true() {
        Shop shop = shop();
        assertThat(shop.isCertified()).isFalse();

        shop.markCertified();

        assertThat(shop.isCertified()).isTrue();
    }

    @Test
    @DisplayName("unmarkCertified() → isCertified false 회복")
    void unmarkCertified_sets_false() {
        Shop shop = shop();
        shop.markCertified();

        shop.unmarkCertified();

        assertThat(shop.isCertified()).isFalse();
    }
}
