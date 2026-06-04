# 04. 내 도메인 API 명세

> Base URL: `/api/v1`

---

## 💰 결제 / 엽전 (Payment)

| Method | Endpoint | 인증 | 동시성 | 설명 |
|--------|----------|------|--------|------|
| POST | `/payments/charge` | USER | 🔴 멱등성 키 필수 | 엽전 충전 요청 |
| POST | `/payments/callback/success` | 서버간 | - | PG 결제 성공 콜백 |
| POST | `/payments/callback/fail` | 서버간 | - | PG 결제 실패 콜백 |
| GET | `/payments/history` | USER | - | 결제 내역 조회 (Cursor) |
| POST | `/payments/{orderId}/refund` | USER | - | 환불 요청 |
| PATCH | `/payments/refund/{refundId}/cancel` | USER | - | 환불 취소 |
| GET | `/admin/refunds` | ADMIN | - | 환불 목록 조회 (Cursor) |
| PATCH | `/admin/refunds/{refundId}/approve` | ADMIN | 🔴 상태 전이 락 | 환불 승인 |
| PATCH | `/admin/refunds/{refundId}/reject` | ADMIN | - | 환불 거절 |

### 엽전 잔액 관련
| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| GET | `/wallets/me` | USER | 내 엽전 잔액 조회 |
| GET | `/wallets/me/histories` | USER | 엽전 사용 내역 조회 (Cursor) |

---

## 🛒 스토어 / 상품 (Store)

| Method | Endpoint | 인증 | 동시성 | 설명 |
|--------|----------|------|--------|------|
| GET | `/store/products` | ❌ | - | 상품 목록 조회 |
| GET | `/store/products/{productId}` | ❌ | - | 상품 상세 조회 |
| POST | `/store/orders` | USER | 🔴 분산 락 + 비관적 락 | 상품 구매 (재고 동시성) |
| GET | `/store/orders` | USER | - | 내 주문 내역 조회 |
| GET | `/users/me/items` | USER | - | 내 보유 아이템 조회 |

### 관리자 상품 관리
| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/admin/store/products` | ADMIN | 상품 등록 |
| PATCH | `/admin/store/products/{productId}` | ADMIN | 상품 수정 |
| DELETE | `/admin/store/products/{productId}` | ADMIN | 상품 삭제 |

---

## 🏪 상인 / 가게 (Merchant / Shop)

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/merchants/apply` | USER | 상인 등록 신청 |
| GET | `/merchants/me/shops` | MERCHANT | 내 가게 목록 조회 |
| GET | `/merchants/me/shops/{shopId}` | MERCHANT | 내 가게 상세 조회 |
| PATCH | `/merchants/me/shops/{shopId}` | MERCHANT | 가게 수정 |
| POST | `/merchants/me/shops/{shopId}/menus` | MERCHANT | 메뉴 등록 |
| PATCH | `/merchants/me/shops/{shopId}/menus/{menuId}` | MERCHANT | 메뉴 수정 |
| DELETE | `/merchants/me/shops/{shopId}/menus/{menuId}` | MERCHANT | 메뉴 삭제 |
| GET | `/merchants/me/home` | MERCHANT | 상인 홈 (오늘 매출, 최근 주문) 🟢 캐싱 TTL 3분 |
| GET | `/merchants/me/shops/{shopId}/qr` | MERCHANT | QR 코드 발급 |
| GET | `/merchants/me/shops/{shopId}/settlements` | MERCHANT | 정산 내역 조회 |
| POST | `/merchants/me/shops/{shopId}/settlements` | MERCHANT | 정산 신청 |
| POST | `/merchants/me/ads` | MERCHANT | 광고 신청 |
| POST | `/merchants/me/shop/images` | MERCHANT | 가게 사진 업로드 |
| PATCH | `/merchants/me/shop/account` | MERCHANT | 정산 계좌 등록 |
| GET | `/merchants/me/shop/wallet` | MERCHANT | 가게 수익 지갑 조회 |

### 관리자 상인 관리 (현민 담당)
| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| GET | `/admin/merchant-applications` | ADMIN | 상인 신청 목록 |
| PATCH | `/admin/merchant-applications/{id}/approve` | ADMIN | 상인 승인 |
| PATCH | `/admin/merchant-applications/{id}/reject` | ADMIN | 상인 거절 |
| PATCH | `/admin/shops/{shopId}/status` | ADMIN | 가게 상태 변경 (ACTIVE↔SUSPENDED) |
| GET | `/admin/ads` | ADMIN | 광고 신청 목록 |
| PATCH | `/admin/ads/{adId}/approve` | ADMIN | 광고 승인 |
| PATCH | `/admin/ads/{adId}/reject` | ADMIN | 광고 거절 |
| GET | `/admin/settlements` | ADMIN | 정산 목록 조회 (?status=PENDING\|APPROVED\|REJECTED, 미지정 시 전체) |
| PATCH | `/admin/settlements/{settlementId}/approve` | ADMIN | 정산 승인 |
| PATCH | `/admin/settlements/{settlementId}/reject` | ADMIN | 정산 거절 (사유 필수) |

> `GET /admin/shops`, `GET /admin/shops/{shopId}`, `PATCH /admin/shops/{shopId}` — 민교 담당

---

## QR 결제 흐름

### 1️⃣ 사용자 화면 (결제 요청)
사용자가 상인 QR 코드 스캔
↓
GET /shops/{shopId}/qr-info
→ 가게명, 메뉴 목록, 가격 조회
↓
사용자: 메뉴 선택 + 수량 입력
↓
POST /payments/qr
Request: {shopId, menuItems: [{menuId, quantity}]}
→ 결제 요청 ID 생성 (qr_pay_request 테이블)
→ qr_pay_request.status = PENDING
↓
사용자 화면: "결제 대기중..." 표시
(상인이 승인할 때까지 대기)

### 2️⃣ 상인 화면 (결제 승인)
상인 대시보드에 새 결제 요청 알림
(Redis Pub/Sub 또는 폴링)
↓
상인: 결제 요청 확인
→ 메뉴, 금액, 요청자 닉네임 표시
↓
상인: "승인" 버튼 클릭
↓
PATCH /payments/qr/{payRequestId}/confirm
Request: {action: "APPROVE"}
↓
[동시성 제어 발동]
1단계: Redis 분산 락 획득
2단계: DB SELECT FOR UPDATE (wallet)
3단계: 사용자 엽전 차감
4단계: 상인 엽전 추가
5단계: yeopjeon_histories 기록
6단계: qr_pay_request.status = COMPLETED
↓
상인/사용자 모두 결제 완료 알림

### 3️⃣ 데이터 변경사항

**사용자 엽전 (Wallet)**

balance: 10,000 → 8,000 (2,000원 차감)


**상인 엽전 (ShopWallet)**

balance: 5,000 → 7,000 (2,000원 추가)


**결제 내역 기록 (yeopjeon_histories)**
사용자 기록:
{
type: "PAYMENT",
amount: -2,000,
description: "QR 결제 - [가게명]",
balanceSnapshot: 8,000
}
상인 기록:
{
type: "RECEIVED_PAYMENT",
amount: +2,000,
description: "[사용자닉네임] QR 결제",
balanceSnapshot: 7,000
}

### 4️⃣ 실패 시나리오

| 실패 지점 | 처리 |
|-----------|------|
| 사용자 엽전 부족 | `PAY_001` 반환, 상인 화면에 "결제 실패" 표시 |
| 분산 락 획득 실패 | `PAY_008` 반환, 상인에게 "처리중" 안내 |
| 상인 승인 거절 | qr_pay_request.status = REJECTED |
| 상인이 요청 타임아웃 (5분 미응답) | 자동 EXPIRED |

### 5️⃣ API 상세

| 메서드 | 엔드포인트 | 인증 | 동시성 | 설명 |
|--------|-----------|------|--------|------|
| GET | `/shops/{shopId}/qr-info` | ❌ | - | 가게 정보 + 메뉴 조회 |
| POST | `/payments/qr` | USER | 🟢 캐싱 | 결제 요청 생성 |
| PATCH | `/payments/qr/{payRequestId}/confirm` | MERCHANT | 🔴 분산 락 | 결제 승인/거절 |
| GET | `/merchants/me/qr-payments/pending` | MERCHANT | - | 상인 대기중 결제 목록 |
이렇게 하면 사용자 → 상인 → 엽전 차감/추가 → 알림 → 내역 기록까지 명확해집니다!

---

## 상인 권한 흐름

```
POST /merchants/apply (USER)
  → 관리자 검토
  → PATCH /admin/merchant-applications/{id}/approve (ADMIN)
  → user.role = MERCHANT 변경
  → 상인 전용 API 접근 가능
```
