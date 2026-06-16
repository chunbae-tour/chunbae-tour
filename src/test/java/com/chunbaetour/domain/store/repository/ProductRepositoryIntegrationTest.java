package com.chunbaetour.domain.store.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * ProductRepository.findForAdmin 실DB 검증 (KAN-300).
 * 관리자 목록 JPQL을 실제 MySQL에 실행 — status=null enum 바인딩·필터·cursor 페이징 정합 확인.
 */
@SpringBootTest
class ProductRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    private Product save(String name, String category, ProductStatus status) {
        return productRepository.save(Product.builder()
                .name(name).description("설명").category(ProductCategory.valueOf(category))
                .price(2000L).originalPrice(3000L).stock(status == ProductStatus.SOLD_OUT ? 0 : 10)
                .originalStock(10).imageUrls(null).merchantName("상인")
                .validityDays(30).status(status).maxPerPerson(5)
                .build());
    }

    @Test
    @DisplayName("findForAdmin — status=null 시 HIDDEN 포함 전체 반환, id 내림차순")
    void findForAdmin_nullStatus_returnsAllIncludingHidden() {
        Product onSale = save("판매중", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        Product soldOut = save("품절", "DISCOUNT_COUPON", ProductStatus.SOLD_OUT);
        Product hidden = save("숨김", "DISCOUNT_COUPON", ProductStatus.HIDDEN);

        List<Product> result = productRepository.findForAdmin(null, null, null, PageRequest.of(0, 10));

        // HIDDEN 포함 3건 전부, id DESC 정렬
        assertThat(result).extracting(Product::getId)
                .containsExactly(hidden.getId(), soldOut.getId(), onSale.getId());
        assertThat(result).extracting(Product::getStatus)
                .contains(ProductStatus.HIDDEN);
    }

    @Test
    @DisplayName("findForAdmin — status 필터 시 해당 상태만 반환")
    void findForAdmin_statusFilter_returnsOnlyMatching() {
        save("판매중", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        Product hidden = save("숨김", "DISCOUNT_COUPON", ProductStatus.HIDDEN);

        List<Product> result =
                productRepository.findForAdmin(ProductStatus.HIDDEN, null, null, PageRequest.of(0, 10));

        assertThat(result).extracting(Product::getId).containsExactly(hidden.getId());
    }

    @Test
    @DisplayName("findForAdmin — category 필터 시 해당 카테고리만 반환")
    void findForAdmin_categoryFilter_returnsOnlyMatching() {
        Product coupon = save("쿠폰", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        save("기프티콘", "LOCAL_PRODUCT", ProductStatus.ON_SALE);

        List<Product> result =
                productRepository.findForAdmin(null, ProductCategory.DISCOUNT_COUPON, null, PageRequest.of(0, 10));

        assertThat(result).extracting(Product::getId).containsExactly(coupon.getId());
    }

    @Test
    @DisplayName("findForAdmin — cursorId 미만 id만 반환 (keyset 페이징)")
    void findForAdmin_cursor_returnsOnlyBelowCursorId() {
        Product first = save("첫번째", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        Product second = save("두번째", "DISCOUNT_COUPON", ProductStatus.HIDDEN);
        Product third = save("세번째", "DISCOUNT_COUPON", ProductStatus.ON_SALE);

        // 가장 큰 id(third) 미만만 조회 → second, first 반환 (id DESC)
        List<Product> result =
                productRepository.findForAdmin(null, null, third.getId(), PageRequest.of(0, 10));

        assertThat(result).extracting(Product::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("findForAdmin — size 만큼만 반환 (Pageable limit)")
    void findForAdmin_respectsPageableLimit() {
        save("상품1", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        save("상품2", "DISCOUNT_COUPON", ProductStatus.ON_SALE);
        save("상품3", "DISCOUNT_COUPON", ProductStatus.ON_SALE);

        List<Product> result = productRepository.findForAdmin(null, null, null, PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }
}
