package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AdminProductService adminProductService;

    @BeforeEach
    void setUp() {
        ProductMapper productMapper = new ProductMapper(new ObjectMapper());
        adminProductService = new AdminProductService(productRepository, redisTemplate, productMapper);
        // 비트랜잭션 컨텍스트에서 즉시 실행되는 Redis 경로 stub — 미사용 시 lenient 처리
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
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
    @DisplayName("관리자 상품 목록 — HIDDEN 포함 전체 status 반환 (KAN-300)")
    void getProducts_includesHiddenProducts() {
        Product onSale = createProduct(10L, 50, ProductStatus.ON_SALE);
        Product hidden = createProduct(9L, 0, ProductStatus.HIDDEN);
        given(productRepository.findForAdmin(eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(onSale, hidden));

        CursorPageResponse<ProductSummaryResponse> result =
                adminProductService.getProducts(null, null, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(ProductSummaryResponse::status)
                .containsExactly(ProductStatus.ON_SALE, ProductStatus.HIDDEN);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("관리자 상품 목록 — status 필터를 레포지토리로 그대로 전달")
    void getProducts_statusFilter_passedToRepository() {
        given(productRepository.findForAdmin(eq(ProductStatus.HIDDEN), eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(createProduct(9L, 0, ProductStatus.HIDDEN)));

        CursorPageResponse<ProductSummaryResponse> result =
                adminProductService.getProducts(ProductStatus.HIDDEN, null, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(ProductStatus.HIDDEN);
        then(productRepository).should()
                .findForAdmin(eq(ProductStatus.HIDDEN), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 상품 목록 — size+1개 반환 시 hasNext=true, nextCursor 발급")
    void getProducts_hasNext_whenExtraItemFetched() {
        given(productRepository.findForAdmin(any(), any(), any(), any()))
                .willReturn(List.of(
                        createProduct(10L, 5, ProductStatus.ON_SALE),
                        createProduct(9L, 5, ProductStatus.HIDDEN),
                        createProduct(8L, 5, ProductStatus.SOLD_OUT))); // size=2 + 1개 초과

        CursorPageResponse<ProductSummaryResponse> result =
                adminProductService.getProducts(null, null, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("관리자 상품 목록 — 공백 카테고리(\"   \")는 null로 정규화해 레포지토리에 전달 (전체 카테고리)")
    void getProducts_blankCategory_normalizedToNull() {
        given(productRepository.findForAdmin(eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(createProduct(10L, 5, ProductStatus.ON_SALE)));

        // 공백만 있는 카테고리는 필터 미적용(null)과 동일하게 처리되어야 한다
        adminProductService.getProducts(null, "   ", null, 20);

        then(productRepository).should()
                .findForAdmin(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 상품 목록 — 카테고리 앞뒤 공백은 trim 후 레포지토리에 전달")
    void getProducts_categoryTrimmed_passedToRepository() {
        given(productRepository.findForAdmin(eq(null), eq("COUPON"), eq(null), any(Pageable.class)))
                .willReturn(List.of(createProduct(10L, 5, ProductStatus.ON_SALE)));

        adminProductService.getProducts(null, "  COUPON  ", null, 20);

        then(productRepository).should()
                .findForAdmin(eq(null), eq("COUPON"), eq(null), any(Pageable.class));
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
        // create 시점 originalStock == stock → soldCount = 0 검증
        assertThat(response.soldCount()).isEqualTo(0);
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
    @DisplayName("상품 삭제 — 200 + status=HIDDEN 응답 반환, Redis 캐시 무효화")
    void deleteProduct_success_returnsHiddenResponseAndEvictsCache() {
        Product product = createProduct(10L, 50, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));

        ProductDetailResponse response = adminProductService.deleteProduct(10L);

        assertThat(response.status()).isEqualTo(ProductStatus.HIDDEN);
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

    @Test
    @DisplayName("상품 등록 — Redis 재고 키(stock:{id}) 초기화")
    void createProduct_setsRedisStockKey() {
        AdminProductCreateRequest req = new AdminProductCreateRequest(
                "테스트", "설명", "COUPON", 2000L, 3000L, 10,
                null, "상인", 30, 5);
        Product saved = createProduct(1L, 10, ProductStatus.ON_SALE);
        given(productRepository.save(any(Product.class))).willReturn(saved);

        adminProductService.createProduct(req);

        then(valueOps).should().set("stock:1", "10");
    }

    @Test
    @DisplayName("상품 삭제(HIDDEN) — Redis 재고 키 삭제")
    void deleteProduct_deletesRedisStockKey() {
        Product product = createProduct(1L, 10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.deleteProduct(1L);

        then(redisTemplate).should().delete("stock:1");
    }

    @Test
    @DisplayName("상품 수정 재고 변경 — Redis 재고 키 갱신")
    void updateProduct_stockChanged_updatesRedisStockKey() {
        Product product = createProduct(1L, 10, ProductStatus.ON_SALE);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, 20, null, null, null, null, null);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.updateProduct(1L, req);

        then(valueOps).should().set("stock:1", "20");
    }

    @Test
    @DisplayName("상품 삭제 — 캐시 키(product:id)와 재고 키(stock:id) 모두 삭제")
    void deleteProduct_deletesBoothCacheKeyAndStockKey() {
        Product product = createProduct(1L, 10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.deleteProduct(1L);

        then(redisTemplate).should().delete("product:1");
        then(redisTemplate).should().delete("stock:1");
    }

    @Test
    @DisplayName("상품 수정 — status=HIDDEN 전환 시 Redis 재고 키 삭제, 세팅 없음")
    void updateProduct_hidden_deletesStockKeyOnly() {
        Product product = createProduct(1L, 10, ProductStatus.ON_SALE);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, ProductStatus.HIDDEN);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.updateProduct(1L, req);

        then(redisTemplate).should().delete("stock:1");
        then(valueOps).should(never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("상품 수정 — status=ON_SALE 재활성화(stock=null) 시 기존 재고로 Redis 키 세팅")
    void updateProduct_onSaleReactivation_setsStockKeyFromExistingStock() {
        Product product = createProduct(1L, 15, ProductStatus.HIDDEN);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.updateProduct(1L, req);

        then(valueOps).should().set("stock:1", "15");
    }

    @Test
    @DisplayName("상품 수정 — status=HIDDEN + stock 동시 요청 시 삭제만 실행, 키 재생성 없음")
    void updateProduct_hiddenWithStock_onlyDeletesStockKey() {
        Product product = createProduct(1L, 10, ProductStatus.ON_SALE);
        AdminProductUpdateRequest req = new AdminProductUpdateRequest(
                null, null, null, null, null, 20, null, null, null, null, ProductStatus.HIDDEN);
        given(productRepository.findByIdWithLock(1L)).willReturn(Optional.of(product));

        adminProductService.updateProduct(1L, req);

        then(redisTemplate).should().delete("stock:1");
        then(valueOps).should(never()).set(anyString(), anyString());
    }
}
