package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.response.ProductCategoryResponse;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private ProductService productService;

    private static final Long PRODUCT_ID = 10L;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        productService = new ProductService(productRepository, redisTemplate, objectMapper, new ProductMapper(objectMapper));
    }

    private Product createProduct(Long id, ProductStatus status) {
        Product p = Product.builder()
                .name("광화문 떡볶이 3000원 할인 쿠폰")
                .description("광화문 떡볶이 할인 쿠폰")
                .category(ProductCategory.DISCOUNT_COUPON)
                .price(2000L)
                .originalPrice(3000L)
                .stock(50)
                .originalStock(100)
                .imageUrls("[\"https://cdn.example.com/1.jpg\"]")
                .merchantName("광화문 떡볶이")
                .validityDays(30)
                .status(status)
                .maxPerPerson(5)
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("상품 목록 조회 — 첫 페이지, 전체 카테고리")
    void getProducts_firstPage_noCategory_returnsList() {
        Product p1 = createProduct(10L, ProductStatus.ON_SALE);
        Product p2 = createProduct(9L, ProductStatus.ON_SALE);
        given(productRepository.findVisibleProducts(
                eq(Set.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT)), eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(p1, p2));

        CursorPageResponse<ProductSummaryResponse> result =
                productService.getProducts(null, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content().get(0).productId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("상품 목록 조회 — hasNext true (size+1개 반환됨)")
    void getProducts_hasNext_whenExtraItemFetched() {
        List<Product> products = List.of(
                createProduct(10L, ProductStatus.ON_SALE),
                createProduct(9L, ProductStatus.ON_SALE),
                createProduct(8L, ProductStatus.ON_SALE) // size=2 + 1개 초과
        );
        given(productRepository.findVisibleProducts(any(), any(), any(), any()))
                .willReturn(products);

        CursorPageResponse<ProductSummaryResponse> result =
                productService.getProducts(null, null, 2);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(2);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("상품 목록 조회 — soldCount = originalStock - stock")
    void getProducts_soldCount_calculatedCorrectly() {
        given(productRepository.findVisibleProducts(any(), any(), any(), any()))
                .willReturn(List.of(createProduct(10L, ProductStatus.ON_SALE)));

        CursorPageResponse<ProductSummaryResponse> result =
                productService.getProducts(null, null, 20);

        assertThat(result.content().get(0).soldCount()).isEqualTo(50); // 100 - 50
    }

    @Test
    @DisplayName("상품 상세 조회 — 캐시 미스, DB 조회 후 캐시 저장")
    void getProduct_cacheMiss_queriesDbAndCaches() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("product:10")).willReturn(null);
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(createProduct(PRODUCT_ID, ProductStatus.ON_SALE)));

        ProductDetailResponse result = productService.getProduct(PRODUCT_ID);

        assertThat(result.productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.name()).isEqualTo("광화문 떡볶이 3000원 할인 쿠폰");
        then(valueOps).should().set(eq("product:10"), any(String.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("상품 상세 조회 — 캐시 히트, DB 조회 생략")
    void getProduct_cacheHit_skipsDb() throws Exception {
        String cachedJson = new ObjectMapper().writeValueAsString(
                new ProductDetailResponse(PRODUCT_ID, "캐시 상품", null,
                        ProductCategoryResponse.from(ProductCategory.DISCOUNT_COUPON),
                        2000L, null, List.of(), null, 50, 50, null, ProductStatus.ON_SALE));

        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("product:10")).willReturn(cachedJson);

        ProductDetailResponse result = productService.getProduct(PRODUCT_ID);

        assertThat(result.productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.name()).isEqualTo("캐시 상품");
        then(productRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("상품 상세 조회 — 존재하지 않는 상품 → PRODUCT_NOT_FOUND")
    void getProduct_notFound_throwsProductNotFound() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(any())).willReturn(null);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("상품 상세 조회 — HIDDEN 상품 → PRODUCT_NOT_FOUND")
    void getProduct_hiddenProduct_throwsProductNotFound() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(any())).willReturn(null);
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(createProduct(PRODUCT_ID, ProductStatus.HIDDEN)));

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("상품 상세 조회 — SOLD_OUT 상품 정상 반환")
    void getProduct_soldOut_returnsNormally() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(any())).willReturn(null);
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(createProduct(PRODUCT_ID, ProductStatus.SOLD_OUT)));

        ProductDetailResponse result = productService.getProduct(PRODUCT_ID);

        assertThat(result.status()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("상품 목록 조회 — stock=0 이어도 ON_SALE 상태면 목록 노출 (정책: STORY-17에서 명시적 전이)")
    void getProducts_onSaleWithZeroStock_visibleInList() {
        Product p = Product.builder()
                .name("재고 소진 쿠폰")
                .category(ProductCategory.DISCOUNT_COUPON)
                .price(1000L)
                .stock(0)
                .originalStock(10)
                .status(ProductStatus.ON_SALE)
                .build();
        ReflectionTestUtils.setField(p, "id", 20L);

        given(productRepository.findVisibleProducts(any(), any(), any(), any()))
                .willReturn(List.of(p));

        CursorPageResponse<ProductSummaryResponse> result =
                productService.getProducts(null, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).stock()).isZero();
        assertThat(result.content().get(0).status()).isEqualTo(ProductStatus.ON_SALE);
    }
}
