package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.request.StorePurchaseRequest;
import com.chunbaetour.domain.store.dto.response.StoreOrderResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.entity.StoreOrder;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.StoreOrderRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스토어 상품 구매 서비스 (STORY-17).
 * 재고 동시성 3단계 보호: Redis 선점 → Redisson 분산 락 → DB 비관적 락.
 * 실패 시 Redis 재고 복구 보장 (finally 블록).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorePurchaseService {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String PURCHASE_LOCK_PREFIX = "purchase:lock:";
    private static final String PRODUCT_CACHE_KEY_PREFIX = "product:";

    private final ProductRepository productRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final UserItemRepository userItemRepository;
    private final WalletService walletService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 상품 구매.
     * [1단계] Redis DECR — 빠른 품절 거부 (DB 진입 전 필터).
     * [2단계] Redisson 분산 락 — 동일 사용자 중복 구매 방지.
     * [3단계] DB SELECT FOR UPDATE — 실제 재고 재검증 + 차감.
     * 예외 발생 시 Redis 재고 복구 보장 (finally 블록).
     */
    @Transactional
    public StoreOrderResponse purchase(Long userId, StorePurchaseRequest request) {
        // 요청에서 상품 ID, 수량 추출
        Long productId = request.productId();
        int quantity = request.quantity().intValue(); // @NotNull 보장 — NPE 없음
        String stockKey = STOCK_KEY_PREFIX + productId;
        // Redis 차감 여부 추적 — 예외 발생 시 finally에서 복구 여부 결정
        boolean redisDecremented = false;
        RLock lock = redissonClient.getLock(PURCHASE_LOCK_PREFIX + userId);

        try {
            // [1단계] Redis 재고 선점 — DB 조회 없이 품절 빠른 거부
            Long remaining = redisTemplate.opsForValue().decrement(stockKey, (long) quantity);
            redisDecremented = true;
            // remaining < 0이면 재고 초과 — 즉시 품절 처리
            if (remaining != null && remaining < 0) {
                throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
            }

            // [2단계] Redisson 분산 락 획득 (3초 대기, 5초 보유)
            // 동일 사용자 동시 구매 요청 중 하나만 통과
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);
            }

            // [3단계] DB 비관적 락(SELECT FOR UPDATE)으로 상품 조회 — stale read 방지
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            // HIDDEN 상품은 존재하지 않는 것으로 처리
            if (product.getStatus() == ProductStatus.HIDDEN) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            // DB 실제 재고 재검증 — Redis와 DB 사이 불일치 보정
            if (product.getStock() < quantity) {
                throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
            }

            // 총 결제 금액 계산 (단가 × 수량)
            long totalPrice = product.getPrice() * quantity;

            // 엽전 차감 — SELECT FOR UPDATE on wallet, 잔액 부족 시 INSUFFICIENT_BALANCE
            walletService.spendForPurchase(userId, totalPrice, product.getName());

            // DB 재고 차감 — 재고 0 도달 시 Product.status → SOLD_OUT 자동 전환
            product.decreaseStock(quantity);

            // 주문 레코드 저장
            StoreOrder order = storeOrderRepository.save(
                    StoreOrder.create(userId, product, quantity, totalPrice));

            // 사용자 보유 아이템 생성 — 수량만큼 각각 1개씩 (개별 사용/만료 관리)
            for (int i = 0; i < quantity; i++) {
                userItemRepository.save(UserItem.create(userId, order.getId(), product));
            }

            // 상품 상세 캐시 무효화 — 재고·상태 변경이 다음 조회에 반영되도록
            try {
                redisTemplate.delete(PRODUCT_CACHE_KEY_PREFIX + productId);
            } catch (Exception e) {
                log.warn("[구매] 상품 캐시 무효화 실패 (productId: {})", productId, e);
            }

            // 구매 성공 — finally에서 Redis 복구 불필요
            redisDecremented = false;
            return StoreOrderResponse.from(order);

        } catch (InterruptedException e) {
            // 락 대기 중 인터럽트 — 스레드 상태 복원 후 처리중 오류 반환
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);
        } finally {
            // 실패 시 Redis 재고 복구 — DB 롤백과 함께 Redis도 일관성 유지
            if (redisDecremented) {
                redisTemplate.opsForValue().increment(stockKey, (long) quantity);
            }
            // 분산 락 해제 (현재 스레드가 보유 중일 때만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 내 주문 내역 조회 — cursor keyset 페이징 (id DESC) */
    public CursorPageResponse<StoreOrderResponse> getMyOrders(Long userId, String cursor, int size) {
        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<StoreOrder> orders = storeOrderRepository.findByUserId(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = orders.size() > size;
        List<StoreOrder> page = hasNext ? orders.subList(0, size) : orders;

        // 엔티티 → 응답 DTO 변환
        List<StoreOrderResponse> content = page.stream()
                .map(StoreOrderResponse::from)
                .toList();

        // 다음 커서: 마지막 항목 ID 인코딩, 없으면 null
        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
