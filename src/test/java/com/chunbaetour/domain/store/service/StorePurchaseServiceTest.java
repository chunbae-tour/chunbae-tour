package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.request.StorePurchaseRequest;
import com.chunbaetour.domain.store.dto.response.StoreOrderResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.entity.StoreOrder;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.StoreOrderRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.store.type.StoreOrderStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePurchaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private StoreOrderRepository storeOrderRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private WalletService walletService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedissonClient redissonClient;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RLock lock;
    @Mock private Clock clock;

    @InjectMocks
    private StorePurchaseService storePurchaseService;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 5_000L;
    private static final String STOCK_KEY = "stock:" + PRODUCT_ID;
    private static final String PRODUCT_CACHE_KEY = "product:" + PRODUCT_ID;
    private static final String LOCK_KEY = "purchase:lock:" + USER_ID;

    @BeforeEach
    void setUp() throws InterruptedException {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
        // Redis 키 존재 기본값 — 개별 테스트에서 override 가능
        lenient().when(redisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        lenient().when(lock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        // Clock 고정 — 2024-01-15 UTC
        lenient().when(clock.instant()).thenReturn(Instant.parse("2024-01-15T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // 기본값: 과거 구매 없음 — 개별 테스트에서 override 가능
        lenient().when(storeOrderRepository.sumQuantityByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(0);
    }

    private Product createProduct(int stock, ProductStatus status) {
        Product product = mock(Product.class);
        given(product.getId()).willReturn(PRODUCT_ID);
        given(product.getName()).willReturn("테스트 상품");
        given(product.getPrice()).willReturn(UNIT_PRICE);
        given(product.getStock()).willReturn(stock);
        given(product.getStatus()).willReturn(status);
        lenient().when(product.getValidityDays()).thenReturn(30);
        lenient().when(product.getMaxPerPerson()).thenReturn(10); // 기본 10 > QUANTITY(2)
        return product;
    }

    private StoreOrder createOrder(Long orderId) {
        StoreOrder order = mock(StoreOrder.class);
        given(order.getId()).willReturn(orderId);
        given(order.getProductId()).willReturn(PRODUCT_ID);
        given(order.getProductName()).willReturn("테스트 상품");
        given(order.getProductPrice()).willReturn(UNIT_PRICE);
        given(order.getQuantity()).willReturn(QUANTITY);
        given(order.getTotalPrice()).willReturn(UNIT_PRICE * QUANTITY);
        given(order.getStatus()).willReturn(StoreOrderStatus.COMPLETED);
        return order;
    }

    @Test
    @DisplayName("구매 성공 — Redis DECR, 분산 락, DB 비관적 락 순서로 처리")
    void purchase_success() throws InterruptedException {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);

        // when
        StoreOrderResponse response = storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY));

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.totalPrice()).isEqualTo(UNIT_PRICE * QUANTITY);
        // 락 순서: decreaseStock 먼저, spendForPurchase 나중
        then(product).should().decreaseStock(QUANTITY);
        then(walletService).should().spendForPurchase(USER_ID, UNIT_PRICE * QUANTITY, "테스트 상품");
        then(userItemRepository).should().saveAll(any());
        then(lock).should().unlock();
    }

    @Test
    @DisplayName("구매 성공 재고 소진 — Product.status SOLD_OUT 전환 검증 (실 인스턴스)")
    void purchase_success_stockDepleted_soldOut() {
        // given — 실제 Product 인스턴스 사용 (decreaseStock 내부 로직 직접 검증)
        Product realProduct = Product.builder()
                .name("재고 소진 상품")
                .description("테스트")
                .category("TEST")
                .price(UNIT_PRICE)
                .originalPrice(null)
                .stock(QUANTITY)       // 정확히 quantity만큼 — 구매 후 stock=0
                .originalStock(QUANTITY)
                .merchantName("테스트 상인")
                .validityDays(30)
                .status(ProductStatus.ON_SALE)
                .maxPerPerson(10)
                .build();
        ReflectionTestUtils.setField(realProduct, "id", PRODUCT_ID);

        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(0L);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(realProduct));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);

        // when
        storePurchaseService.purchase(USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY));

        // then — 재고 0 → SOLD_OUT 자동 전환
        assertThat(realProduct.getStock()).isZero();
        assertThat(realProduct.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("1인 구매 한도 초과 — PURCHASE_QUANTITY_EXCEEDED 반환, Redis 복구")
    void purchase_fail_maxPerPersonExceeded() {
        // given: maxPerPerson=1 < QUANTITY=2
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(product.getMaxPerPerson()).willReturn(1);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_QUANTITY_EXCEEDED);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("누적 구매량 합산 초과 — 단건은 한도 이내여도 PURCHASE_QUANTITY_EXCEEDED 반환")
    void purchase_fail_cumulativeLimitExceeded() {
        // given: maxPerPerson=3, 이미 2개 구매, 이번 요청 2개 → 합산 4 > 3
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(6L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(product.getMaxPerPerson()).willReturn(3);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        given(storeOrderRepository.sumQuantityByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(2);

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_QUANTITY_EXCEEDED);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("Redis 재고 없음 — PRODUCT_SOLD_OUT 반환, Redis 복구")
    void purchase_fail_redisStockEmpty() {
        // given: Redis remaining < 0
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(-1L);

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);

        // Redis 복구 확인
        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
        // DB 진입 안 함
        then(productRepository).should(never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("Redis 키 미세팅 — DB 단독 처리 (1단계 건너뜀)")
    void purchase_redis_key_missing() {
        // given: Redis 키 없음 → hasKey = false
        given(redisTemplate.hasKey(STOCK_KEY)).willReturn(false);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);

        // when — 예외 없이 구매 성공
        StoreOrderResponse response = storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY));

        // then — Redis DECR 호출 안 함, DB로 정상 처리
        assertThat(response.orderId()).isEqualTo(1L);
        then(valueOps).should(never()).decrement(anyString(), anyLong());
    }

    @Test
    @DisplayName("SOLD_OUT 상품 — PRODUCT_SOLD_OUT 반환, Redis 복구")
    void purchase_fail_soldOutProduct() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(0, ProductStatus.SOLD_OUT);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
        then(walletService).should(never()).spendForPurchase(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("분산 락 획득 실패 — PURCHASE_PROCESSING 반환, Redis 복구")
    void purchase_fail_lockAcquisitionFailed() throws InterruptedException {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        given(lock.tryLock(3, TimeUnit.SECONDS)).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_PROCESSING);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("DB 재고 부족 (3단계 재검증) — PRODUCT_SOLD_OUT 반환, Redis 복구")
    void purchase_fail_dbStockInsufficient() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(1, ProductStatus.ON_SALE); // DB 재고 1 < 요청 2
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
        then(walletService).should(never()).spendForPurchase(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("상품 없음 — PRODUCT_NOT_FOUND 반환, Redis 복구")
    void purchase_fail_productNotFound() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("HIDDEN 상품 — PRODUCT_NOT_FOUND 반환, Redis 복구")
    void purchase_fail_hiddenProduct() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.HIDDEN);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("잔액 부족 — INSUFFICIENT_BALANCE 반환, Redis 복구")
    void purchase_fail_insufficientBalance() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        willThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE))
                .given(walletService).spendForPurchase(eq(USER_ID), anyLong(), anyString());

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
        then(storeOrderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UserItem 저장 실패 — 예외 전파, Redis 복구")
    void purchase_fail_userItemSaveFailed() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);
        willThrow(new RuntimeException("DB 오류"))
                .given(userItemRepository).saveAll(any());

        // when & then — 예외 전파 (트랜잭션 롤백 대상)
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(RuntimeException.class);

        // 트랜잭션 동기화 미활성(단위테스트) 환경에서 finally fallback으로 Redis 복구
        then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 afterCompletion에서 Redis 재고를 복구")
    void purchase_fail_transactionRollback_recoversRedisStockAfterCompletion() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);
        willThrow(new RuntimeException("DB 오류"))
                .given(userItemRepository).saveAll(any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            // when & then
            assertThatThrownBy(() -> storePurchaseService.purchase(
                    USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                    .isInstanceOf(RuntimeException.class);

            then(valueOps).should(never()).increment(STOCK_KEY, (long) QUANTITY);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            then(valueOps).should().increment(STOCK_KEY, (long) QUANTITY);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 커밋 후 상품 상세 캐시를 무효화하고 Redis 재고는 복구하지 않음")
    void purchase_success_transactionCommit_deletesProductCacheAfterCommit() {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        Product product = createProduct(10, ProductStatus.ON_SALE);
        given(productRepository.findByIdWithLock(PRODUCT_ID)).willReturn(Optional.of(product));
        StoreOrder order = createOrder(1L);
        given(storeOrderRepository.saveAndFlush(any(StoreOrder.class))).willReturn(order);

        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            storePurchaseService.purchase(USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.get(0).afterCommit();
            synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            // then
            then(redisTemplate).should().delete(PRODUCT_CACHE_KEY);
            then(valueOps).should(never()).increment(STOCK_KEY, (long) QUANTITY);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("내 주문 내역 조회 — 첫 페이지 (hasNext=false)")
    void getMyOrders_firstPage() {
        // given
        StoreOrder order1 = createOrder(2L);
        StoreOrder order2 = createOrder(1L);
        given(storeOrderRepository.findOrdersByUserIdWithCursor(eq(USER_ID), eq(null), any()))
                .willReturn(List.of(order1, order2));

        // when
        CursorPageResponse<StoreOrderResponse> result =
                storePurchaseService.getMyOrders(USER_ID, null, 20);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("내 주문 내역 조회 — 다음 페이지 존재 (hasNext=true, cursor 반환)")
    void getMyOrders_hasNext() {
        // given: size=2 요청, size+1=3개 반환 → hasNext=true
        StoreOrder order1 = createOrder(3L);
        StoreOrder order2 = createOrder(2L);
        StoreOrder order3 = createOrder(1L);
        given(storeOrderRepository.findOrdersByUserIdWithCursor(eq(USER_ID), eq(null), any()))
                .willReturn(List.of(order1, order2, order3));

        // when
        CursorPageResponse<StoreOrderResponse> result =
                storePurchaseService.getMyOrders(USER_ID, null, 2);

        // then — size=2만 반환, hasNext=true, nextCursor는 노출된 마지막 항목(order2, id=2) 기반
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        // idExtractor 배선 검증: sentinel(order3)이 아니라 노출 마지막 항목(order2=2L) id를 인코딩해야 함
        assertThat(CursorUtils.decode(result.nextCursor())).isEqualTo(2L);
    }
}
