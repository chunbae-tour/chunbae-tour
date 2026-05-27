package com.chunbaetour.domain.store.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.type.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Product createProduct(int stock) {
        return Product.builder()
                .name("테스트 상품")
                .description("설명")
                .category("TEST")
                .price(1_000L)
                .originalPrice(null)
                .stock(stock)
                .originalStock(stock)
                .merchantName("상인")
                .validityDays(30)
                .status(ProductStatus.ON_SALE)
                .build();
    }

    @Test
    @DisplayName("decreaseStock 정상 — 재고 차감")
    void decreaseStock_success() {
        Product product = createProduct(10);
        product.decreaseStock(3);
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("decreaseStock 재고 소진 — status SOLD_OUT 자동 전환")
    void decreaseStock_stockDepleted_soldOut() {
        Product product = createProduct(2);
        product.decreaseStock(2);
        assertThat(product.getStock()).isZero();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("decreaseStock quantity=0 — INVALID_PURCHASE_QUANTITY")
    void decreaseStock_zeroQuantity_throws() {
        Product product = createProduct(10);
        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PURCHASE_QUANTITY);
    }

    @Test
    @DisplayName("decreaseStock quantity 음수 — INVALID_PURCHASE_QUANTITY")
    void decreaseStock_negativeQuantity_throws() {
        Product product = createProduct(10);
        assertThatThrownBy(() -> product.decreaseStock(-1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PURCHASE_QUANTITY);
    }

    @Test
    @DisplayName("decreaseStock 재고 부족 — PRODUCT_SOLD_OUT")
    void decreaseStock_insufficientStock_throws() {
        Product product = createProduct(1);
        assertThatThrownBy(() -> product.decreaseStock(2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);
    }
}
