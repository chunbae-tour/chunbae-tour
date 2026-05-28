# AdminReportResolveIntegrationTest 수정 필요 사항 (KAN-93)

## 배경

KAN-93에서 MERCHANT 신고의 `targetId = shopId`로 설계 확정 (다중 가게 지원).
기존 통합 테스트(KAN-92)는 `targetId = merchant.getId()` (accountId) 기준으로 작성되어 있음.
develop 머지 충돌 방지를 위해 통합 테스트는 원본 유지 → 팀원 협의 후 반영.

## 수정이 필요한 파일

`src/test/java/com/chunbaetour/domain/report/AdminReportResolveIntegrationTest.java`

---

## 변경 내용

### 1. import 추가

```java
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
```

### 2. 필드 추가

```java
@Autowired private ShopRepository shopRepository;
```

### 3. @AfterEach cleanup에 shop 삭제 추가

```java
@AfterEach
void cleanup() {
    reportRepository.deleteAll();
    commentRepository.deleteAll();
    companionPostRepository.deleteAll();
    freePostRepository.deleteAll();
    shopRepository.deleteAll();   // ← 추가
    accountRepository.deleteAll();
}
```

### 4. dismiss_merchant_report 수정

```java
// 기존
Account merchant = seedFactory.seedMerchant("mdm@test.com", PASSWORD, "기각가게주인");
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, merchant.getId(), ...));

// 변경
Account merchant = seedFactory.seedMerchant("mdm@test.com", PASSWORD, "기각가게주인");
Shop shop = shopRepository.save(Shop.builder()
        .userId(merchant.getId()).applicationId(1L)
        .shopName("기각가게").category("FOOD").address("서울시 테스트구").build());
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, shop.getId(), ...));
```

### 5. revoke_merchant_downgrades_role 수정

```java
// 기존
Account merchant = seedFactory.seedMerchant("rmerchant@test.com", PASSWORD, "강등상인");
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, merchant.getId(), ...));

// 변경
Account merchant = seedFactory.seedMerchant("rmerchant@test.com", PASSWORD, "강등상인");
Shop shop = shopRepository.save(Shop.builder()
        .userId(merchant.getId()).applicationId(1L)
        .shopName("강등가게").category("FOOD").address("서울시 테스트구").build());
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, shop.getId(), ...));
```

### 6. hide_shop_records_resolved 수정

```java
// DisplayName 변경
// 기존: "HIDE_SHOP — Shop 미구현이라도 status=RESOLVED"
// 변경: "HIDE_SHOP — 가게 SUSPENDED 처리 후 status=RESOLVED"

// 기존
Account merchant = seedFactory.seedMerchant("hsmerchant@test.com", PASSWORD, "숨김상인");
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, merchant.getId(), ...));

// 변경
Account merchant = seedFactory.seedMerchant("hsmerchant@test.com", PASSWORD, "숨김상인");
Shop shop = shopRepository.save(Shop.builder()
        .userId(merchant.getId()).applicationId(1L)
        .shopName("숨김가게").category("FOOD").address("서울시 테스트구").build());
Report report = reportRepository.save(Report.create(
        reporter.getId(), ReportTargetType.MERCHANT, shop.getId(), ...));
```

---

## 핵심 이유

- MERCHANT 신고 `targetId = shopId` (가게 ID) — 상인 1명이 여러 가게 보유 가능
- `validateReportTarget` MERCHANT: `shopService.findMerchantAccountId(shopId)` → 가게 owner 검증
- `applyMerchantAction` HIDE_SHOP: `shopService.hideShop(shopId)` 직접 호출
- `applyMerchantAction` REVOKE_MERCHANT: shopId → accountId 변환 후 계정 강등
