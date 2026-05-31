package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StringRedisTemplate redisTemplate;

    private AdminProductService adminProductService;

    @BeforeEach
    void setUp() {
        ProductMapper productMapper = new ProductMapper(new ObjectMapper());
        adminProductService = new AdminProductService(productRepository, redisTemplate, productMapper);
    }

    private Product createProduct(Long id, int stock, ProductStatus status) {
        Product p = Product.builder()
                .name("테스트 상품").description("설명").category("COUPON")
                .price(2000L).originalPrice(3000L).stock(stock).originalStock(stock)
                .imageUrls(null).merchantName("상인").validityDays(30)
                .status(status).maxPerPerson(5).build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("상품 등록 — ProductDetailResponse 반환, status = ON_SALE")
    void createProduct_success_returnsDetail() {
        AdminProductCreateRequest req = new AdminProductCreateRequest(
                "새 상품", "설명", "COUPON", 2000L, null, 30, null, "상인", 30, 3);
        given(productRepository.save(any(Product.class)))
                .willAnswer(inv -> {
                    Product p = inv.getArgument(0);
                    ReflectionTestUtils.setField(p, "id", 99L);
                    return p;
                });

        ProductDetailResponse response = adminProductService.createProduct(req);

        assertThat(response.name()).isEqualTo("새 상품");
        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
        then(productRepository).should().save(any(Product.class));
    }

    @Test
    @DisplayName("상품 수정 — 수정 후 Redis 캐시 무효화")
    void updateProduct_success_evictsCache() {
        Product product = createProduct(10L, 50, ProductStatus.ON_SALE);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                "수정 상품", null, null, null, null, null, null, null, null, null, null);
        given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));

        ProductDetailResponse response = adminProductService.updateProduct(10L, req);

        assertThat(response.name()).isEqualTo("수정 상품");
        then(redisTemplate).should().delete("product:10");
    }

    @Test
    @DisplayName("상품 수정 — 존재하지 않는 상품 → PRODUCT_NOT_FOUND")
    void updateProduct_notFound_throws() {
        given(productRepository.findByIdWithLock(999L)).willReturn(Optional.empty());
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> adminProductService.updateProduct(999L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        then(redisTemplate).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("상품 삭제 — status HIDDEN + Redis 캐시 무효화")
    void deleteProduct_success_hiddenAndEvictsCache() {
        Product product = createProduct(10L, 50, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));

        adminProductService.deleteProduct(10L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        then(redisTemplate).should().delete("product:10");
    }

    @Test
    @DisplayName("상품 삭제 — 존재하지 않는 상품 → PRODUCT_NOT_FOUND")
    void deleteProduct_notFound_throws() {
        given(productRepository.findByIdWithLock(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminProductService.deleteProduct(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        then(redisTemplate).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("상품 수정 — stock 감소(정정) 시 originalStock도 재조정, soldCount 허위 방지 (#2)")
    void updateProduct_stockDecrease_recalibratesOriginalStock() {
        // originalStock=100, stock=100인 상품을 stock=50으로 감소 정정
        Product product = createProduct(10L, 100, ProductStatus.ON_SALE);
        ReflectionTestUtils.setField(product, "originalStock", 100);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, 50, null, null, null, null, null);
        given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));

        adminProductService.updateProduct(10L, req);

        // originalStock도 50으로 재조정 → soldCount = 50-50 = 0 (허위 50 판매 방지)
        assertThat(product.getOriginalStock()).isEqualTo(50);
        assertThat(product.getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("상품 수정 — status=ON_SALE + stock=0 조합 명시 시 INVALID_REQUEST (#3)")
    void updateProduct_onSaleWithZeroStock_throwsInvalidRequest() {
        Product product = createProduct(10L, 50, ProductStatus.ON_SALE);
        // stock=0으로 줄이면서 status=ON_SALE 명시 → invariant 위반
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, 0, null, null, null, null, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> adminProductService.updateProduct(10L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
