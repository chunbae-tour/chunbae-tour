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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final Clock clock;

    /**
     * 상품 구매.
     * [1단계] Redis DECR — 빠른 품절 거부 (키 없으면 DB 단독 처리로 폴백).
     * [2단계] Redisson 분산 락 — 동일 사용자 중복 구매 방지 (per-userId 직렬화).
     * [3단계] DB SELECT FOR UPDATE — 실제 재고 재검증 + 차감.
     * 예외 발생 시 Redis 재고 복구 보장 (finally 블록).
     * 락 순서: Product(findByIdWithLock) → Wallet(spendForPurchase) — 데드락 방지.
     */
    @Transactional
    public StoreOrderResponse purchase(Long userId, StorePurchaseRequest request) {
        Long productId = request.productId();
        int quantity = request.quantity().intValue(); // @NotNull 보장 — NPE 없음
        String stockKey = STOCK_KEY_PREFIX + productId;
        // Redis 차감 여부 추적 — AtomicBoolean으로 afterCompletion 람다 캡처 가능
        AtomicBoolean redisDecremented = new AtomicBoolean(false);
        // 트랜잭션 동기화 등록 여부 — finally 직접 복구(단위테스트 fallback)와 이중 복구 방지
        boolean synchronizationRegistered = false;
        // 구매 성공 여부 — 단위테스트 fallback에서 실패 경로 판별용
        boolean succeeded = false;
        RLock lock = redissonClient.getLock(PURCHASE_LOCK_PREFIX + userId);

        try {
            // 트랜잭션 완료 콜백 등록 (Redis 복구 + 캐시 무효화)
            // isSynchronizationActive() 가드: 단위테스트(트랜잭션 없음)에서 ISE 방지
            // 단위테스트에서는 finally 직접 복구(fallback) 경로로 동작
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        // COMMITTED 외(ROLLED_BACK·UNKNOWN) 시 Redis 재고 복구
                        // UNKNOWN = DB 장애·네트워크 단절 등 커밋 여부 불확실 → 보수적으로 복구
                        if (redisDecremented.get() && status != TransactionSynchronization.STATUS_COMMITTED) {
                            redisTemplate.opsForValue().increment(stockKey, (long) quantity);
                        }
                    }
                    @Override
                    public void afterCommit() {
                        // 커밋 확정 후 캐시 무효화 — 롤백 시엔 DB도 원복되므로 캐시 갱신 불필요
                        try {
                            redisTemplate.delete(PRODUCT_CACHE_KEY_PREFIX + productId);
                        } catch (Exception e) {
                            log.warn("[구매] 상품 캐시 무효화 실패 (productId: {})", productId, e);
                        }
                    }
                });
                synchronizationRegistered = true;
            }

            // [1단계] Redis 재고 선점 — 키 없으면 DB 단독 처리로 폴백 (상품 활성화 시 Redis 키 사전 세팅 필요)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                Long remaining = redisTemplate.opsForValue().decrement(stockKey, (long) quantity);
                redisDecremented.set(true);
                // remaining < 0이면 재고 초과 — 즉시 품절 처리
                if (remaining != null && remaining < 0) {
                    throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
                }
            } else {
                // Redis 키 미세팅 상태 — DB 재검증(3단계)에서 재고 확인
                log.warn("[구매] Redis 재고 키 미세팅 (productId: {}) — DB 단독 처리", productId);
            }

            // [2단계] Redisson 분산 락 획득 (3초 대기, leaseTime 미설정 → watchdog 자동 갱신)
            // 동일 사용자 동시 구매 요청 중 하나만 통과 — per-userId 직렬화
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);
            }

            // [3단계] DB 비관적 락(SELECT FOR UPDATE)으로 상품 조회 — stale read 방지
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            // HIDDEN 상품은 존재하지 않는 것으로 처리
            if (product.getStatus() == ProductStatus.HIDDEN) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            // 1인당 최대 구매 수량 검증 — 과거 누적 구매량 포함 (단건 요청만 검사하면 반복 구매로 우회 가능)
            int alreadyPurchased = storeOrderRepository.sumQuantityByUserIdAndProductId(userId, productId);
            if (alreadyPurchased + quantity > product.getMaxPerPerson()) {
                throw new BusinessException(ErrorCode.PURCHASE_QUANTITY_EXCEEDED);
            }
            // SOLD_OUT 상품 명시적 차단 — Redis·DB 재고 드리프트 방어
            if (product.getStatus() == ProductStatus.SOLD_OUT) {
                throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
            }
            // DB 실제 재고 재검증 — Redis와 DB 사이 불일치 보정
            if (product.getStock() < quantity) {
                throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
            }

            long totalPrice = product.getPrice() * quantity;

            // DB 재고 차감 먼저 — 락 순서 Product(이미 획득) → Wallet 준수
            product.decreaseStock(quantity);

            // 엽전 차감 — SELECT FOR UPDATE on wallet, 잔액 부족 시 INSUFFICIENT_BALANCE
            walletService.spendForPurchase(userId, totalPrice, product.getName());

            // saveAndFlush — UserItem에 orderId 전달 전 flush로 PK 확보
            StoreOrder order = storeOrderRepository.saveAndFlush(
                    StoreOrder.create(userId, product, quantity, totalPrice));

            // 사용자 보유 아이템 생성 — 수량만큼 각각 1개씩 (saveAll로 batch INSERT)
            LocalDate today = LocalDate.now(clock);
            List<UserItem> items = new ArrayList<>();
            for (int i = 0; i < quantity; i++) {
                items.add(UserItem.create(userId, order.getId(), product, today));
            }
            userItemRepository.saveAll(items);

            succeeded = true;
            return StoreOrderResponse.from(order);

        } catch (InterruptedException e) {
            // 락 대기 중 인터럽트 — 스레드 상태 복원 후 처리중 오류 반환
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);
        } finally {
            // 단위테스트·비트랜잭션 컨텍스트 fallback — 프로덕션에서는 afterCompletion이 처리
            if (!succeeded && !synchronizationRegistered && redisDecremented.get()) {
                redisTemplate.opsForValue().increment(stockKey, (long) quantity);
            }
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception e) {
                log.warn("[구매] 분산 락 해제 실패 (userId: {})", userId, e);
            }
        }
    }

    /** 내 주문 내역 조회 — cursor keyset 페이징 (id DESC) */
    public CursorPageResponse<StoreOrderResponse> getMyOrders(Long userId, String cursor, int size) {
        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<StoreOrder> orders = storeOrderRepository.findOrdersByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(orders, size, StoreOrderResponse::from, StoreOrder::getId);
    }
}
