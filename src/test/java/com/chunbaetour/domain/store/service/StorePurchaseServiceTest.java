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
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.request.StorePurchaseRequest;
import com.chunbaetour.domain.store.dto.response.StoreOrderResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.entity.StoreOrder;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.StoreOrderRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.store.type.StoreOrderStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks
    private StorePurchaseService storePurchaseService;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 5_000L;
    private static final String STOCK_KEY = "stock:" + PRODUCT_ID;
    private static final String LOCK_KEY = "purchase:lock:" + USER_ID;

    @BeforeEach
    void setUp() throws InterruptedException {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
        lenient().when(lock.tryLock(3, 5, TimeUnit.SECONDS)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private Product createProduct(int stock, ProductStatus status) {
        Product product = mock(Product.class);
        given(product.getId()).willReturn(PRODUCT_ID);
        given(product.getName()).willReturn("테스트 상품");
        given(product.getPrice()).willReturn(UNIT_PRICE);
        given(product.getStock()).willReturn(stock);
        given(product.getStatus()).willReturn(status);
        lenient().when(product.getValidityDays()).thenReturn(30);
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
        given(storeOrderRepository.save(any(StoreOrder.class))).willReturn(order);

        // when
        StoreOrderResponse response = storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY));

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.totalPrice()).isEqualTo(UNIT_PRICE * QUANTITY);
        then(walletService).should().spendForPurchase(USER_ID, UNIT_PRICE * QUANTITY, "테스트 상품");
        then(product).should().decreaseStock(QUANTITY);
        then(userItemRepository).should(org.mockito.Mockito.times(QUANTITY)).save(any(UserItem.class));
        then(lock).should().unlock();
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
    @DisplayName("분산 락 획득 실패 — PURCHASE_PROCESSING 반환, Redis 복구")
    void purchase_fail_lockAcquisitionFailed() throws InterruptedException {
        // given
        given(valueOps.decrement(STOCK_KEY, (long) QUANTITY)).willReturn(8L);
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        // when & then
        assertThatThrownBy(() -> storePurchaseService.purchase(
                USER_ID, new StorePurchaseRequest(PRODUCT_ID, QUANTITY)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_PROCESSING);

        // Redis 복구 확인
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
        then(storeOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내 주문 내역 조회 — cursor 페이징")
    void getMyOrders_success() {
        // given
        StoreOrder order1 = createOrder(2L);
        StoreOrder order2 = createOrder(1L);
        given(storeOrderRepository.findByUserId(eq(USER_ID), eq(null), any()))
                .willReturn(List.of(order1, order2));

        // when
        CursorPageResponse<StoreOrderResponse> result =
                storePurchaseService.getMyOrders(USER_ID, null, 20);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
    }
}
