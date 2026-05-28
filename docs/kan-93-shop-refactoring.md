# KAN-93 Shop 도메인 리팩토링 변경 내역

## 변경 목적

- `ReportService`가 `ShopRepository`를 직접 의존하는 구조 제거 → 도메인 경계 준수
- `ShopService`를 통해 가게 숨김·상인 계정 조회 로직 캡슐화
- `HIDDEN` 상태 가게의 공개 노출 차단 로직 추가 (공개 숨김 완성)
- `resolveTargetDetail` MERCHANT 케이스 버그 수정 (shopId를 accountId로 잘못 조회하던 문제)

---

## 수정 파일 목록

| 파일 | 변경 종류 |
|------|-----------|
| `ShopService.java` | 메서드 2개 추가, `getShopInfo()` 수정 |
| `ReportService.java` | 의존성 교체, 메서드 3개 수정 + 버그 수정 |
| `ShopServiceTest.java` | 테스트 7개 추가 |
| `ReportServiceTest.java` | Mock 추가, 테스트 2개 추가 |

---

## 1. `ShopService.java`

### 추가: `hideShop(Long shopId)`

```java
@Transactional
public void hideShop(Long shopId) { ... }
```

- 역할: 신고 처리(HIDE_SHOP 액션) 시 가게 status를 HIDDEN으로 변경
- CLOSED 가게에 호출 시 `Shop.hide()` 내부 `IllegalStateException` → `SHOP_INACTIVE`로 변환
- ReportService가 ShopRepository를 직접 쓰지 않도록 진입점 역할 위임

### 추가: `findMerchantAccountId(Long shopId)`

```java
public Optional<Long> findMerchantAccountId(Long shopId) { ... }
```

- 역할: shopId → 상인 accountId(userId) 변환
- 신고 처리(REVOKE_MERCHANT) 및 MERCHANT 자기신고 검증에 사용
- 가게 없으면 `Optional.empty()` 반환 — 에러 코드는 호출 측에서 결정

### 수정: `getShopInfo(Long shopId)` — HIDDEN 차단 추가

**변경 전:**
```java
Shop shop = shopRepository.findById(shopId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
// 바로 메뉴 조회 후 반환 (HIDDEN도 그냥 반환됨)
```

**변경 후:**
```java
Shop shop = shopRepository.findById(shopId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

// HIDDEN 가게는 공개 노출 차단
if (shop.getStatus() == ShopStatus.HIDDEN) {
    throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);  // 존재 여부 노출 방지
}
```

- HIDDEN만 차단, SUSPENDED/CLOSED는 기존 정책 유지(조회 허용)
- 에러 코드를 SHOP_NOT_FOUND로 통일해 가게 존재 여부 노출 방지

---

## 2. `ReportService.java`

### 변경: 의존성 교체

**변경 전:**
```java
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
...
private final ShopRepository shopRepository;
```

**변경 후:**
```java
import com.chunbaetour.domain.shop.service.ShopService;
...
private final ShopService shopService;
```

### 변경: `applyMerchantAction()` — ShopRepository 직접 호출 제거

**변경 전:**
```java
case HIDE_SHOP -> {
    Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    shop.hide();
}
case REVOKE_MERCHANT -> {
    Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    Account merchant = accountRepository.findById(shop.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    merchant.revokeToUser();
}
```

**변경 후:**
```java
case HIDE_SHOP -> shopService.hideShop(shopId);
case REVOKE_MERCHANT -> {
    Long merchantAccountId = shopService.findMerchantAccountId(shopId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    Account merchant = accountRepository.findById(merchantAccountId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    merchant.revokeToUser();
}
```

### 변경: `validateReportTarget()` MERCHANT 케이스 — ShopRepository 직접 호출 제거

**변경 전:**
```java
case MERCHANT -> {
    Shop shop = shopRepository.findById(targetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
    if (shop.getUserId().equals(reporterId)) {
        throw new BusinessException(ErrorCode.REPORT_SELF);
    }
}
```

**변경 후:**
```java
case MERCHANT -> {
    Long ownerId = shopService.findMerchantAccountId(targetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
    if (ownerId.equals(reporterId)) {
        throw new BusinessException(ErrorCode.REPORT_SELF);
    }
}
```

### 버그 수정: `resolveTargetDetail()` MERCHANT 케이스 — shopId를 accountId로 잘못 조회하던 문제

**변경 전 (버그):**
```java
case USER, MERCHANT -> accountRepository.findById(targetId)  // MERCHANT는 targetId=shopId → accountId 아님!
        .map(a -> new TargetDetail(null, a.getNickname(), null))
        .orElse(TargetDetail.empty());
```

**변경 후 (수정):**
```java
case USER -> accountRepository.findById(targetId)
        .map(a -> new TargetDetail(null, a.getNickname(), null))
        .orElse(TargetDetail.empty());
case MERCHANT -> shopService.findMerchantAccountId(targetId)  // shopId → accountId 변환 후 닉네임 조회
        .flatMap(accountRepository::findById)
        .map(a -> new TargetDetail(null, a.getNickname(), null))
        .orElse(TargetDetail.empty());
```

- MERCHANT `targetId`는 `shopId` (accountId 아님) — 기존 코드는 shopId로 Account를 조회하는 버그
- `findMerchantAccountId`로 shopId → accountId 변환 후 닉네임 조회

---

## 3. `ShopServiceTest.java` — 추가 테스트

| 테스트명 | 검증 내용 |
|----------|-----------|
| `getShopInfo_hidden_throws` | HIDDEN 가게 → SHOP_NOT_FOUND, 메뉴 조회 없음 |
| `hideShop_success` | ACTIVE 가게 숨김 → status HIDDEN |
| `hideShop_notFound_throws` | 가게 없음 → SHOP_NOT_FOUND |
| `hideShop_closedShop_throws` | CLOSED 가게 숨김 → SHOP_INACTIVE |
| `findMerchantAccountId_success` | shopId → userId 반환 |
| `findMerchantAccountId_notFound_returnsEmpty` | 가게 없음 → Optional.empty() |

총 기존 13개 + 신규 6개 = **19개** (모두 통과)

---

## 4. `ReportServiceTest.java` — Mock 추가 및 신규 테스트

### Mock 추가
```java
@Mock private ShopService shopService;
```

### 추가 테스트

| 테스트명 | 검증 내용 |
|----------|-----------|
| `create_MERCHANT_가게없음` | shopId 없음 → REPORT_TARGET_NOT_FOUND |
| `create_MERCHANT_본인가게신고` | shopId ≠ reporterId이지만 owner가 본인 → REPORT_SELF |

총 기존 22개 + 신규 2개 = **24개** (모두 통과)

---

## 테스트 결과 요약

```
ShopServiceTest  : 19 tests, 0 failures, 0 errors ✅
ReportServiceTest: 24 tests, 0 failures, 0 errors ✅
```
