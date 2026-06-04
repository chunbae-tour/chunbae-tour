# 03. 동시성 제어 가이드

> 내 도메인(결제/스토어)의 핵심 기술 책임 영역이다. 반드시 숙지하고 구현한다.

---

## 전략 선택 기준

| 시나리오 | 전략 | 이유 |
|----------|------|------|
| 한정 수량 상품 구매 | Redis 가점유 + 분산 락 + DB 비관적 락 (3단계) | 충돌 빈도 높음 + 정합성 필수 |
| 엽전 차감 (QR 결제) | Redis 분산 락 + DB 비관적 락 | 금전 처리, 정합성 필수 |
| 환불 상태 전이 | DB 비관적 락 | 상태 변경 정합성 |

---

## 시나리오 1: 한정 수량 상품 구매 (3단계 보호)

```
구매 요청
  │
  ├─ [1단계] Redis DECR stock:{productId}
  │   결과 < 0 → INCR 복구 + STORE_002 (품절)
  │
  ├─ [2단계] Redisson 분산 락 획득 (purchase:lock:{userId})
  │   실패 → INCR 복구 + STORE_005 (처리중)
  │
  ├─ [3단계] DB SELECT FOR UPDATE (비관적 락)
  │   재고 없음 → INCR 복구 + 락 해제 + STORE_002
  │
  ├─ 엽전 차감 or PG 결제
  │
  ├─ 성공 → DB 재고 확정 + 락 해제
  └─ 실패 → INCR 복구 + 락 해제 + 트랜잭션 롤백
```

```java
// 구현 패턴
Long remaining = redisTemplate.opsForValue().decrement("stock:" + productId);
if (remaining < 0) {
    redisTemplate.opsForValue().increment("stock:" + productId);
    throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
}

RLock lock = redissonClient.getLock("purchase:lock:" + userId);
try {
    if (!lock.tryLock(3, TimeUnit.SECONDS)) {
        redisTemplate.opsForValue().increment("stock:" + productId);
        throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);
    }
    Product product = productRepository.findByIdWithLock(productId); // SELECT FOR UPDATE
    if (product.getStock() <= 0) {
        redisTemplate.opsForValue().increment("stock:" + productId);
        throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
    }
    // 결제 처리
    product.decreaseStock();
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

---

## 시나리오 2: 엽전 차감 (QR 결제)

```
엽전 결제 요청
  │
  ├─ Redisson 분산 락 획득 (payment:lock:{userId})
  │   실패 → PAY_008 (처리중)
  │
  ├─ DB SELECT FOR UPDATE (wallet)
  │
  ├─ 잔액 검증 balance >= amount
  │   부족 → 락 해제 + PAY_001
  │
  ├─ 잔액 차감 + yeopjeon_histories INSERT
  │
  ├─ 성공 → 락 해제 + 커밋
  └─ 실패 → 락 해제 + 롤백
```

---

## Redisson 설정 기준

```java
// 락 획득 대기시간: 3초
// leaseTime 미지정: Redisson watchdog 자동 연장 활성화
lock.tryLock(3, TimeUnit.SECONDS)
```

주의: `tryLock(waitTime, leaseTime, unit)`처럼 leaseTime을 명시하면 watchdog 자동 연장이 동작하지 않는다.
구매/결제처럼 처리 시간이 외부 요인(DB/Redis/PG)에 따라 늘어날 수 있는 비즈니스 락은 leaseTime을 생략한다.

---

## 실패 시나리오 → 에러코드 매핑

| 실패 지점 | 에러코드 |
|-----------|----------|
| Redis 재고 없음 | `STORE_002` (품절) |
| 분산 락 획득 실패 (구매) | `STORE_005` (처리중) |
| 분산 락 획득 실패 (결제) | `PAY_008` (처리중) |
| 잔액 부족 | `PAY_001` |
| DB 재고 없음 (3단계) | `STORE_002` |
| 멱등성 키 중복 | `PAY_007` |
