# 12. SA 문서 대비 실제 구현 변경 이력

> SA(설계) 문서와 실제 구현이 달라진 지점을 기록한다.
> MVP 완료 후 중간점검 시 이 문서를 기준으로 원본 문서를 일괄 수정한다.
>
> **기록 기준**: 에러코드 추가/제거, 엔드포인트 변경, ERD 컬럼 추가/제거, 정책 변경 등.
> 단순 오타 수정은 기록 불필요.

---

## 1. API 엔드포인트 변경

| 도메인 | SA 문서 엔드포인트 | 실제 구현 | 이유 |
|--------|------------------|-----------|------|
| 엽전 잔액 조회 | `GET /wallets/me` | `GET /api/v1/yeopjeon/balance` | `/wallet/me`는 지갑 전체 조회처럼 읽혀 네이밍 부적절 |
| 엽전 이력 조회 | `GET /wallets/me/histories` | `GET /api/v1/yeopjeon/histories` | 엽전 도메인 컨트롤러로 통합, `/wallet` 경로 제거 |
| PG 콜백 (성공) | `POST /payments/callback/success` | `POST /api/v1/payments/webhook` 으로 통합 | PortOne V2는 서버→서버 웹훅 단일 방식 — success/fail 분리 불필요 |
| PG 콜백 (실패) | `POST /payments/callback/fail` | 동일 (`/payments/webhook`으로 통합) | 동상 |
| 가게 공개 조회 | `GET /yeopjeon/qr/shops/{shopId}` 또는 `/shops/{shopId}/qr-info` | `GET /api/v1/shops/{shopId}` | QR 전용 네이밍 부적절 — QR 스캔 외 앱 탐색 등 다양한 진입 경로 존재. 가게 공개 정보 조회 목적이므로 일반 리소스 경로로 변경 |

---

## 2. 정책 변경

| 도메인 | 항목 | SA 문서 | 실제 구현 | 이유 |
|--------|------|---------|-----------|------|
| 결제 | 최소 충전 금액 | 1,000원 이상 (PAY_002) | **5,000원** 이상 | 정책 결정 |
| 결제 | 웹훅 서명 검증 | 검증 없음 | HMAC-SHA256 서명 검증 추가 (`WebhookVerifier`) | 보안 강화 — 위조 웹훅 차단 |
| 환불 | PATCH reject body | body 없이 호출 | **선택적** body `{ "reason": "..." }` 추가 | 관리자가 거절 사유 기록 가능하도록 개선, body 없이도 동작(하위 호환) |
| 환불 | GET admin/refunds 응답 | rejectReason 없음 | `RefundDetailResponse`에 `rejectReason` 필드 추가 | 관리자 거절 사유 조회 필요 — additive 변경, 하위 호환 |
| 메뉴 | 가격 최솟값 | 0원 이상 | **1원** 이상 (`@Min(1)`) | 0원 메뉴는 결제 시스템에서 의미 없음 |
| 가게 공개 조회 | `GET /shops/{shopId}` 응답: shopId, shopName, qrCodeUrl, qrPayload | address, phone, description, operatingHours, closedDays 추가 | 가게 탐색 시 위치·연락처·운영시간 정보 필요 — 결제 화면 구성에도 사용 |
| 상인 신청 | `getApplications` PENDING 고정 | status 파라미터 추가 (defaultValue=PENDING) | 관리자가 APPROVED/REJECTED 내역 감사 조회 가능해야 함 |

---

## 3. ERD 변경 (컬럼 추가/수정)

| 테이블 | 항목 | SA 문서 | 실제 구현 | 이유 |
|--------|------|---------|-----------|------|
| `shops` | `rating` 타입 | `FLOAT` | `DOUBLE` | 부동소수점 정밀도 향상 |
| `shops` | `application_id` 제약 | 일반 FK | `UNIQUE` 제약 추가 (`uk_shops_application_id`) | 동일 신청서로 가게 2개 생성 방지 (동시 승인 race condition 차단) |
| `merchant_applications` | `business_number` 제약 | 일반 컬럼 | `UNIQUE` 제약 추가 (`uk_merchant_applications_business_number`) | 동일 사업자번호 중복 신청 동시 race condition 차단 |
| `menus` | `deleted_at` | 없음 | `TIMESTAMP NULL` 추가, soft delete 적용 (`@SQLRestriction`) | QR 결제 내역(qr_pay_requests.menu_items)에서 menuId 참조 보존 — hard delete 시 영수증에서 메뉴 정보 유실 |

---

## 4. 에러코드 변경

| 에러코드 | SA 문서 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| SHOP_005 | `INVALID_MENU_PRICE` — 메뉴 가격 0원 이상 | **제거** | DTO `@Min(1)` 검증으로 충분 |
| SHOP_006 | `SHOP_NAME_TOO_LONG` — 가게명 50자 초과 | **제거** | DTO `@Size(max=50)` 검증으로 충분 |
| SHOP_005 (재사용) | 없음 | `SHOP_INACTIVE` — 정지/폐업 가게 수정 불가 | SHOP_005/006 제거로 번호 당겨짐 (SA 문서 상 SHOP_007) |
| MERCHANT_004 | 없음 | `DUPLICATE_BUSINESS_NUMBER` — 이미 등록된 사업자번호 | 사업자번호 UK 제약 추가로 신규 에러코드 필요 |
| MERCHANT_005 | 없음 | `MERCHANT_APPLICATION_STATUS_INVALID` — 허용되지 않는 상태 전이 | 관리자 승인/거절 상태 가드 |
| PAY_012~021 | 없음 | WALLET_NOT_FOUND, PAYMENT_AMOUNT_MISMATCH, WEBHOOK_SIGNATURE_INVALID 등 | STORY-03~07 구현 과정에서 추가 |
| SHOP_005 (메시지 변경) | "정지 또는 폐업 상태의 가게는 수정할 수 없습니다" | "정지 또는 폐업 상태의 가게입니다." | STORY-13에서 QR 결제에도 재사용 — 수정 맥락에 한정된 문구 제거, 중립 메시지로 변경 |
| SHOP_006 | `MENU_UNAVAILABLE` — 현재 주문할 수 없는 메뉴 (품절/비활성) | 재추가 | KAN-107 리팩토링에서 제거했다가 STORY-13 QR 결제 메뉴 검증에 필요해 재추가 |
| PAY_022 | 없음 | `DUPLICATE_QR_PAY_REQUEST` — 이미 대기 중인 QR 결제 요청 존재 | STORY-13 중복 PENDING 방지 |
| PAY_023 | 없음 | `SELF_PAYMENT_NOT_ALLOWED` — 본인 가게 자가결제 차단 | STORY-13 자가결제 방어 |
| PAY_024 | 없음 | `ZERO_AMOUNT_NOT_ALLOWED` — 0원 결제 요청 차단 | STORY-13 메뉴 price=0 데이터 방어 |
| PAY_021 (삭제) | `INVALID_PAGE_SIZE` — 페이지 크기 1~100 검증 | **COMMON_010으로 이동** | 결제 전용 에러가 아닌 공통 페이징 검증 — 다른 도메인도 동일 기준 적용 가능. 서비스 레이어 검증 제거하고 컨트롤러 `@Min(1) @Max(100)`으로 이동과 함께 변경 |

---

## 5. SA 문서와 다른 컨트롤러 위치

| 항목 | SA 문서 위치 | 실제 구현 위치 | 이유 |
|------|------------|--------------|------|
| 웹훅 처리 | `payment/controller/PaymentCallbackController.java` | `payment/controller/WebhookController.java` | PortOne V2 웹훅 방식으로 변경에 따라 파일명 변경 |

---

## 6. MVP 완료 후 수정 필요한 SA 문서 목록

| 문서 | 수정 내용 |
|------|-----------|
| `docs/05_ERD.md` | menus 테이블 `deleted_at` 컬럼 추가, shops.rating DOUBLE, UK 제약 추가 |
| `docs/06_API_명세서.md` | 엔드포인트 변경 반영 (yeopjeon/balance, yeopjeon/histories, payments/webhook) |
| `docs/12_공통_에러코드_설계서.md` | SHOP_005/006 제거, SHOP_005=SHOP_INACTIVE, MERCHANT_004/005, PAY_012~021 추가 |
| `hanes/05-ERROR-CODES.md` | 위 에러코드 변경 반영 |
| `hanes/04-API-GUIDE.md` | 엔드포인트 변경 반영 (`/shops/{shopId}` 포함) |

---

## 7. KAN-115 구현 변경 사항

| 항목 | 변경 전 | 변경 후 | 이유 |
|------|---------|---------|------|
| `RefundResponse` 응답 스키마 | `refundId, paymentOrderId, amount, status` | `createdAt` 필드 추가 | 클라이언트의 7일 만료 기준 표시 및 정렬에 필요. TODO 조건(조회 API 추가) 충족으로 추가. 프론트 클라이언트 동시 업데이트 필요 (브레이킹 체인지) |
| `RefundRepository` 사용자 조회 메서드 | `findByUserIdOrderByIdDesc`, `findByUserIdAndIdLessThanOrderByIdDesc`, `findByUserIdAndStatusOrderByIdDesc`, `findByUserIdAndStatusAndIdLessThanOrderByIdDesc` (4개) | `findByUserIdWithFilter` 1개 (JPQL, IS NULL OR 패턴) | 4가지 조합 중복 제거. Hibernate 6.x에서 enum null 파라미터 정상 처리 확인 |
| `AdminRefundService` 관리자 조회 메서드 | `findAllOrderByIdDesc`, `findByIdLessThanOrderByIdDesc` (2개) | `findWithCursor` 1개 (JPQL, IS NULL OR 패턴) | 동일 이유 |
| `CursorUtils` | `encode()`, `decode()` 2개 | `decodeSafe()` 추가 — null 처리 + INVALID_CURSOR 예외 변환 내장 | RefundService·AdminRefundService의 private decodeCursorSafe() 중복 제거. 커서 사용 API는 decodeSafe() 사용 통일 |
| 페이지 크기 검증 위치 | 서비스 레이어 직접 검증 (BusinessException) | `PaymentController` + `AdminRefundController`에 `@Validated` + `@Min(1) @Max(100)` | Spring 표준 Bean Validation 방식으로 통일. 다른 컨트롤러와 일관성 유지. INVALID_PAGE_SIZE 에러코드 COMMON_010으로 이동 연동 |

---

## 8. STORY-12/13 구현 완료 확인 사항

> ✅ **QR 결제 스냅샷**: `qr_pay_requests.menu_items` JSON에 결제 시점 스냅샷 저장 완료.
> - 저장 형식: `[{"menuId": 1, "name": "떡볶이", "price": 5000, "quantity": 2}]`
> - `QrPayService.createQrPayRequest()` 내 Menu 조회 → MenuSnapshotItem 구성 → ObjectMapper 직렬화.

---

## 9. STORY-12 코드리뷰 반영 변경 사항 (KAN-110)

### ERD 변경

| 테이블 | 항목 | SA 문서 | 실제 구현 | 이유 |
|--------|------|---------|-----------|------|
| `qr_pay_requests` | `pending_key` 컬럼 | 없음 | `VARCHAR NULL UNIQUE` 추가 | TOCTOU 중복 PENDING 방지. PENDING 상태엔 `{userId}_{shopId}` 값 세팅, COMPLETED/REJECTED/EXPIRED 전환 시 NULL 초기화 → MySQL NULL은 UNIQUE 제약 우회하므로 완료 건 복수 허용 |

### API 응답 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| `GET /shops/{shopId}` 응답 `rating` 타입 | `double` | `BigDecimal` | IEEE 754 부동소수점 NaN/Infinity JSON 직렬화 스펙 위반 방지 |
| `GET /shops/{shopId}` 응답 | `status` 필드 없음 | `ShopStatus status` 추가 | 클라이언트가 SUSPENDED 등 상태를 "영업정지"로 표시 가능해야 함. 목록 숨김은 별도 STORY |

### 에러코드 변경

| 에러코드 | 변경 전 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| PAY_025 | 없음 | `QR_PAY_INVALID_STATUS_TRANSITION` (409 CONFLICT) | QrPayRequest 상태 전이 가드 — 이미 COMPLETED/REJECTED/EXPIRED 건에 대한 재처리 시도 차단 |

---

## 10. STORY-14 구현 변경 사항 (KAN-135 QR 결제 승인/거절)

### API 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| QR 결제 확정 엔드포인트 | `POST /yeopjeon/qr/confirm` | `PATCH /api/v1/payments/qr/{payRequestId}/confirm` | REST 멱등성 원칙 — 상태 전이는 PATCH, 리소스 식별자는 경로 파라미터로 분리 |
| QR 결제 확정 권한 | USER 또는 미정 | MERCHANT 전용 (`hasRole("MERCHANT")`) | 결제 승인/거절은 상인(가게 소유자)만 가능 — SecurityConfig 명시 |

### 동시성 제어 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 락 전략 | 미정 | 분산 락(Redisson) + 비관적 락(락-이후-재조회) 2단계 | 동시 승인/거절 직렬화 + stale PENDING 재처리 방지 |
| 락 순서 | 미정 | `wallets` → `shop_wallets` 고정 | 데드락 방지 |
| REJECT 처리 위치 | 락 외부 가능 | 분산 락 이후로 이동 | 동시 APPROVE·REJECT 경합 시 일관성 보장 |

### 에러코드 추가

| 에러코드 | SA 문서 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| SHOP_WALLET_NOT_FOUND | 없음 | `SHOP_WALLET_NOT_FOUND` (404) | shop_wallets 레코드 미존재 시 — 상인 지갑 초기화 누락 감지 |
| PAYMENT_PROCESSING | 없음 | `PAYMENT_PROCESSING` (409) | 분산 락 타임아웃 — 동일 가게·사용자에 다른 결제 처리 중 |

### ERD 변경

| 테이블 | 항목 | SA 문서 | 실제 구현 | 이유 |
|--------|------|---------|-----------|------|
| `shop_wallets` | 신규 테이블 | 없음 | `id, shop_id(UK), balance, version` | 상인 엽전 잔액 분리 관리 — 사용자 wallets 테이블과 구분 |

---

## 11. STORY-15 구현 변경 사항 (KAN-145 QR 결제 만료 스케줄러)

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 만료 처리 방식 | 미정 | `@Scheduled(fixedDelay)` 5분 주기 배치 | 단순 배치로 충분 — 실시간 TTL 이벤트 불필요 |
| 만료 대상 조건 | `expiredAt < now` | `status = PENDING AND expiredAt < now` | COMPLETED·REJECTED는 이미 처리된 건 — 재전이 방지 |

---

## 12. STORY-16 구현 변경 사항 (KAN-150 스토어 상품 목록/상세)

### API 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 상품 목록 엔드포인트 | `GET /api/stores/{storeId}/products` | `GET /api/v1/store/products` | storeId 경로 파라미터 없음 — 전체 상품 목록 API (가게별 필터는 미구현, category 필터만 제공) |
| 상품 상세 엔드포인트 | `GET /api/stores/{storeId}/products/{productId}` | `GET /api/v1/store/products/{productId}` | 동일 이유 |
| 인증 여부 | 미정 | 비인증 공개 API (`permitAll`) | 상품 탐색은 비로그인 사용자도 가능해야 함 |

### 구현 정책 결정 (SA 문서 미정→확정)

| 항목 | 결정 내용 |
|------|-----------|
| 목록 노출 상태 | `ON_SALE`, `SOLD_OUT` 화이트리스트 — `HIDDEN`만 제외하는 블랙리스트 대신, 신규 status 추가 시 의도적 결정 강제 |
| SOLD_OUT 목록 노출 | 노출 유지 — 품절 상태 전이는 STORY-17(구매 흐름)에서 명시적 처리. 클라이언트가 `status` 필드로 품절 표시 |
| stock=0 + status=ON_SALE | 목록 노출, status 그대로 반환 — 상태 전이는 구매 시점에 처리 (STORY-17) |
| 캐시 HIDDEN 처리 | 캐시 내 `status` 확인만 수행 (DB 재조회 없음) — 상품 HIDDEN 전환 시 캐시 무효화는 STORY-17에서 처리 |

### DTO 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| `ProductSummaryResponse.status` | 없음 | `ProductStatus status` 추가 | 클라이언트가 목록에서 품절 여부 명확히 판별 가능해야 함 |
| `status` 타입 | `String` | `ProductStatus` enum | 타입 안전성, enum→String 변환 비용 제거, 오타 컴파일 에러 가드 |

### 에러코드 추가

| 에러코드 | SA 문서 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| STORE_001 | `PRODUCT_NOT_FOUND` (404) | 동일 | HIDDEN 상품·존재하지 않는 상품 모두 404로 통일 — 존재 여부 노출 방지 |

---

## 13. STORY-17 구현 변경 사항 (KAN-158 스토어 상품 구매)

### API 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 구매 엔드포인트 | `POST /api/v1/stores/{storeId}/orders` | `POST /api/v1/store/orders` | storeId 경로 파라미터 없음 — 전체 스토어 단일 도메인, STORY-16 패턴 통일 |
| 내 주문 내역 조회 | `GET /api/v1/stores/{storeId}/orders` | `GET /api/v1/store/orders` | 동일 이유 |

### 동시성 제어 (SA 문서 미정→확정)

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 재고 제어 전략 | 미정 | Redis DECR → Redisson 분산 락 → DB SELECT FOR UPDATE 3단계 | 처리량 최대화 + 정합성 보장. Redis 선점으로 DB 부하 최소화 |
| Redis 키 미세팅 처리 | 미정 | `hasKey` 체크 후 false면 Redis 건너뜀, DB 단독 처리 | 캐시 초기화 전 상품 또는 키 만료 시 서비스 중단 방지 |
| 락 순서 | 미정 | `Product(findByIdWithLock)` → `Wallet(spendForPurchase)` 고정 | 데드락 방지 — 항상 Product 락 먼저 획득 후 Wallet |
| 실패 시 복구 | 미정 | `redisDecremented` 플래그 + finally 블록에서 Redis increment | DB 롤백과 Redis 재고 일관성 동시 보장 |

### DTO 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| `StorePurchaseRequest.quantity` 타입 | `int` (미정) | `@NotNull @Min(1) @Max(99) Integer` (boxed) | `@NotNull`은 primitive에 동작 안 함 — QrPayItemRequest 패턴 통일 |

### ERD 변경

| 테이블 | 항목 | SA 문서 | 실제 구현 | 이유 |
|--------|------|---------|-----------|------|
| `store_orders` | 인덱스 | 미정 | `(user_id, id)` 복합 인덱스 | cursor keyset 페이징 최적화 — userId 필터 + id DESC 정렬 |
| `user_items` | `expiresAt` 타임존 | 미정 | `LocalDate.now(ZoneId.of("Asia/Seoul"))` 명시 | 서버 JVM 타임존 의존 방지 |
| `user_items` | `UserItem` 생성 방식 | 미정 | 수량만큼 개별 레코드, `saveAll()` 배치 INSERT | 쿠폰/티켓 개별 상태 관리(사용/만료 각각 추적) 필요. N번 save() 대신 saveAll()로 배치 최적화 |

### 에러코드 추가

| 에러코드 | SA 문서 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| PURCHASE_PROCESSING | 없음 | `PURCHASE_PROCESSING` (409) | Redisson 분산 락 타임아웃 — 동일 userId 동시 구매 요청 직렬화 |

---

## 14. STORY-19 구현 변경 사항 (KAN-160 정산 신청 + 관리자 처리)

### API 변경

| 항목 | SA 문서 | 실제 구현 | 이유 |
|------|---------|-----------|------|
| 관리자 정산 목록 | 미정 | `GET /api/v1/admin/settlements` 추가 | 관리자가 PENDING 건 목록 확인 후 승인/거절 필요 |

### 정책 추가 (SA 문서 미정→확정)

| 항목 | 결정 내용 |
|------|-----------|
| 최소 정산 금액 | 5,000엽전 (50,000원) — 소액 정산 반복 시 수동 이체 운영 비용 > 정산액 문제 방지 |
| 거절 사유 | 필수(`@NotBlank`) — 상인에게 거절 이유 미고지 시 민원 발생 가능 |
| 잔액 차감 시점 | 신청 시점 아닌 **관리자 승인 시점**에 ShopWallet 차감 — 승인 전까지 QR 수입 계속 누적 가능 |
| 정산 금액 스냅샷 | 신청 시점 ShopWallet 잔액 전액 스냅샷 → 승인 시 해당 금액 차감 |
| 계좌 정보 스냅샷 | 신청 시점 bankName/accountNumber/accountHolder 스냅샷 — 이후 계좌 변경 시 정산 대상 계좌 불변 보장 |

### 동시성 제어 (SA 문서 미정→확정)

| 항목 | 결정 내용 |
|------|-----------|
| 락 전략 | DB 비관적 락(SELECT FOR UPDATE) — QR 결제 분산 락과 달리 정산은 저빈도 작업이라 단순 비관적 락으로 충분 |
| 락 순서 | `Settlement → ShopWallet` 고정 — 데드락 방지 |

### 에러코드 추가

| 에러코드 | SA 문서 | 실제 구현 | 이유 |
|----------|---------|-----------|------|
| SHOP_008 | 없음 | `SETTLEMENT_NOT_FOUND` (404) | 정산 ID 없음 |
| SHOP_009 | 없음 | `DUPLICATE_SETTLEMENT_REQUEST` (409) | 이미 PENDING 정산 존재 시 중복 차단 |
| SHOP_010 | 없음 | `SETTLEMENT_INVALID_STATUS` (409) | PENDING 아닌 건 승인/거절 시도 차단 |
| SHOP_011 | 없음 | `SETTLEMENT_BALANCE_EMPTY` (400) | ShopWallet 잔액 0 — PAY_001(사용자 엽전)과 맥락 분리 |
| SHOP_012 | 없음 | `SETTLEMENT_AMOUNT_TOO_LOW` (400) | 최소 5,000엽전 미달 |

---

## 15. KAN-160 추가 변경 사항 (정산 + 다중 가게 설계 변경)

### 다중 가게 설계 변경

| 항목 | 변경 전 | 변경 후 | 이유 |
|------|---------|---------|------|
| `shops` 테이블 `user_id` 제약 | `UNIQUE` (`uk_shops_user_id`) | 제약 없음 | 1상인 다중 가게 허용 — 시장 상인이 여러 가게를 운영하는 실제 사례 반영 |
| `ShopRepository` | `findByUserId(Long userId)` (Optional) | `findAllByUserId(Long userId)` (List) + `findByIdAndUserId(Long id, Long userId)` | 단건 조회 → 목록 조회로 전환, 소유권 검증은 id+userId 복합으로 처리 |
| `ShopService` | `getMyShop(userId)` (단건) | `getMyShops(userId)` (목록) + `getMyShop(userId, shopId)` (단건 소유권 검증) | 다중 가게 반환 구조 변경 |
| `ShopController` | `GET /merchants/me/shop` | `GET /merchants/me/shops` (목록), `GET/PATCH /merchants/me/shops/{shopId}` | shopId 경로 파라미터로 가게 특정 |
| `MenuController` 경로 | `/merchants/me/shop/menus` | `/merchants/me/shops/{shopId}/menus` | 가게별 메뉴 관리 |
| `SettlementController` 경로 | `/merchants/me/settlements` | `/merchants/me/shops/{shopId}/settlements` | 가게별 정산 신청/조회 |
| `MerchantQrController` 경로 | `/merchants/me/qr` | `/merchants/me/shops/{shopId}/qr` | 가게별 QR 발급 |
| `AdminMerchantApplicationService` | `existsByUserId` 중복 체크 + `uk_shops_user_id` catch | 두 항목 모두 제거 | uk_shops_user_id 제약 삭제로 불필요 |

### API 변경

| 항목 | 변경 전 | 변경 후 | 이유 |
|------|---------|---------|------|
| 관리자 정산 목록 status 필터 | 없음 | `?status=PENDING\|APPROVED\|REJECTED` (선택, 미지정 시 전체) | 관리자가 PENDING 건만 확인하거나 APPROVED 이력 조회 가능해야 함 |

### 계좌번호 마스킹 수정

| 항목 | 변경 전 | 변경 후 | 이유 |
|------|---------|---------|------|
| `SettlementResponse.maskAccountNumber()` | 문자열 substring 기반 → 하이픈 제거 버그 | digit-index 기반 (앞 3자리 숫자·뒤 4자리 숫자 노출, 중간 * 치환, 하이픈 보존) | `"123-4567-8901"` → `"123-****-8901"` 형태로 하이픈 유지 필요 |
