# KAN-185 리팩토링 백로그

코드리뷰(DuJjoneKoo, p2e1011-cpu, CodeRabbit) 기준 2026-06-01.
KAN-193 PR에 store 도메인 코드가 혼입돼 리뷰됨 → 별도 브랜치에서 반영 필요.

---

## 🔴 1. imageUrls 빈 배열(=[]) 전달 시 이미지 전체 삭제 불가 버그

**위치:** `AdminProductService.updateProduct()`

**문제:** `request.imageUrls() = []` → `serializeImageUrls([])` → `null` 반환
→ `null`은 "수정 없음"으로 처리 → 기존 이미지 유지 (관리자가 이미지 전체 삭제 불가)

**수정:**
```java
String imageUrlsJson = null;
if (request.imageUrls() != null) {
    imageUrlsJson = request.imageUrls().isEmpty()
            ? "[]"
            : productMapper.serializeImageUrls(request.imageUrls());
}
```

---

## 🟡 2. Product 도메인 불변식 검증 보강

**위치:** `Product.create()`, `Product.adminUpdate()`

**문제:** DTO @Validation 우회 경로(내부 서비스 호출, 배치, 테스트)에서 잘못된 값이 저장 가능.

### create()에 추가
```java
if (originalPrice != null && originalPrice < price) throw new BusinessException(INVALID_REQUEST);
if (validityDays != null && validityDays < 1)       throw new BusinessException(INVALID_REQUEST);
if (maxPerPerson < 1)                               throw new BusinessException(INVALID_REQUEST);
```

### adminUpdate()에 추가 (필드 반영 전)
```java
if (price != null && price < 1)               throw new BusinessException(INVALID_REQUEST);
if (stock != null && stock < 0)               throw new BusinessException(INVALID_REQUEST);
if (validityDays != null && validityDays < 1) throw new BusinessException(INVALID_REQUEST);
if (maxPerPerson != null && maxPerPerson < 1) throw new BusinessException(INVALID_REQUEST);
```

**추가:** adminUpdate()는 price/originalPrice를 먼저 반영 후 검증하는 구조 →
"최종 값 계산 → 검증 → 실제 필드 반영" 순서로 리팩토링 권장.

---

## 🟡 3. DTO 검증 불일치

**위치:** `AdminProductCreateRequest`, `AdminProductUpdateRequest`

| 필드 | 현재 | 문제 | 수정 |
|------|------|------|------|
| `validityDays` | 어노테이션 없음 | 음수 허용 | `@Positive` |
| `stock` (Create) | `@Min(0)` | 도메인에서 `stock <= 0` 예외 → DTO 통과 후 도메인 실패 | `@NotNull @Min(1)` |

---

## 🟡 4. imageUrls 원소 검증 부재

**위치:** `AdminProductCreateRequest`, `AdminProductUpdateRequest`

**문제:** `@Size(max=10)`으로 개수만 제한. 빈 문자열 원소 허용.
`ProductMapper.parseImageUrls()`는 blank 필터링하므로 저장값 ≠ 응답값 불일치 가능.

**수정:**
```java
@Size(max = 10)
List<@NotBlank @Size(max = 500) String> imageUrls
```

---

## 🟢 5. ProductDetailResponse에 maxPerPerson 누락

**위치:** `ProductMapper.toDetail()`

**문제:** 응답에 `maxPerPerson` 없음 → 관리자/프론트에서 구매 제한 수량 확인 불가.
`ProductDetailResponse` DTO + `toDetail()` 매핑에 필드 추가 필요.
