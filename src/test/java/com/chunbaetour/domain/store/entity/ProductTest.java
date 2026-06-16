package com.chunbaetour.domain.store.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Product createProduct(int stock) {
        return Product.builder()
                .name("테스트 상품").description("설명").category(ProductCategory.DISCOUNT_COUPON)
                .price(1_000L).originalPrice(null).stock(stock).originalStock(stock)
                .merchantName("상인").validityDays(30)
                .status(ProductStatus.ON_SALE).maxPerPerson(5).build();
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

    @Test
    @DisplayName("create — 초기 status = ON_SALE, originalStock = stock")
    void create_initialStatus_onSaleAndOriginalStockSet() {
        Product product = Product.create("테스트 상품", "설명", ProductCategory.DISCOUNT_COUPON, 2000L, 3000L, 50, null, "상인", 30, 5);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.getStock()).isEqualTo(50);
        assertThat(product.getOriginalStock()).isEqualTo(50);
        assertThat(product.getPrice()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("adminUpdate — null 필드는 기존 값 유지")
    void adminUpdate_nullFields_keepOriginalValues() {
        Product product = createProduct(10);
        product.adminUpdate(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(product.getName()).isEqualTo("테스트 상품");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("adminUpdate — stock 추가 + SOLD_OUT이면 ON_SALE 복구, originalStock 갱신")
    void adminUpdate_stockAdded_soldOutRecoveredToOnSale() {
        Product product = Product.builder()
                .name("품절상품").description("").category(ProductCategory.DISCOUNT_COUPON).price(1000L)
                .originalPrice(null).stock(0).originalStock(10)
                .merchantName(null).validityDays(null)
                .status(ProductStatus.SOLD_OUT).maxPerPerson(1).build();
        product.adminUpdate(null, null, null, null, null, 20, null, null, null, null, null);
        assertThat(product.getStock()).isEqualTo(20);
        assertThat(product.getOriginalStock()).isEqualTo(20);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("adminUpdate — stock=0으로 설정 시 ON_SALE → SOLD_OUT 자동 전환")
    void adminUpdate_stockSetToZero_onSaleToSoldOut() {
        Product product = createProduct(10);
        product.adminUpdate(null, null, null, null, null, 0, null, null, null, null, null);
        assertThat(product.getStock()).isZero();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("adminUpdate — status 명시 시 직접 전환")
    void adminUpdate_statusExplicit_overrides() {
        Product product = createProduct(10);
        product.adminUpdate(null, null, null, null, null, null, null, null, null, null, ProductStatus.HIDDEN);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    @DisplayName("softDelete — status HIDDEN 전환")
    void softDelete_setsStatusHidden() {
        Product product = createProduct(10);
        product.softDelete();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    @DisplayName("adminUpdate — stock 감소 정정 시 originalStock 재조정 (허위 soldCount 방지)")
    void adminUpdate_stockDecrease_recalibratesOriginalStock() {
        Product product = Product.builder()
                .name("상품").description("").category(ProductCategory.DISCOUNT_COUPON).price(1000L)
                .originalPrice(null).stock(100).originalStock(100)
                .merchantName(null).validityDays(null)
                .status(ProductStatus.ON_SALE).maxPerPerson(1).build();
        product.adminUpdate(null, null, null, null, null, 50, null, null, null, null, null);
        assertThat(product.getStock()).isEqualTo(50);
        assertThat(product.getOriginalStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("adminUpdate — status=ON_SALE + stock=0 명시 시 INVALID_REQUEST (invariant)")
    void adminUpdate_onSaleWithZeroStock_throwsInvalidRequest() {
        Product product = createProduct(50);
        assertThatThrownBy(() ->
                product.adminUpdate(null, null, null, null, null, 0, null, null, null, null, ProductStatus.ON_SALE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("create — stock=0으로 생성 시 INVALID_REQUEST (ON_SALE 불변식)")
    void create_zeroStock_throwsInvalidRequest() {
        assertThatThrownBy(() ->
                Product.create("상품", "설명", ProductCategory.DISCOUNT_COUPON, 2000L, null, 0, null, "상인", 30, 5))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("adminUpdate — price 인상 후 originalPrice < price 최종 상태 시 INVALID_REQUEST")
    void adminUpdate_priceExceedsOriginalPrice_throwsInvalidRequest() {
        // originalPrice=3000인 상품에서 price를 5000으로 인상 → originalPrice(3000) < price(5000) 위반
        Product product = Product.builder()
                .name("상품").description("").category(ProductCategory.DISCOUNT_COUPON).price(2000L)
                .originalPrice(3000L).stock(10).originalStock(10)
                .merchantName(null).validityDays(null)
                .status(ProductStatus.ON_SALE).maxPerPerson(1).build();
        assertThatThrownBy(() ->
                product.adminUpdate(null, null, null, 5000L, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
