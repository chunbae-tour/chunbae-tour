# ❌ 미구현 API 목록

---

## 🔴 필수 — 없으면 기능 구멍

| # | 엔드포인트 | 설명 | 비고 |
|---|-----------|------|------|
| 1 | `GET /yeopjeon/qr/status/{paymentRequestId}` | QR 결제 상태 폴링 | 푸시 미도달 시 사용자 확인 수단 없음. STORY-13/14 작업 시 함께 구현 |

---

## 🟡 명세서 있음, 미구현

| # | 엔드포인트 | 설명 | 비고 |
|---|-----------|------|------|
| 2 | `GET /admin/ad-applications/{applicationId}` | 광고 신청 단건 상세 | 목록 조회만 구현됨, 단건 없음 |
| 3 | `POST /admin/ads` | 광고 직접 등록 (대면 계약) | 별도 관리자 플로우 |

---

## ⚪ 선택 — 나중에 구현

| # | 엔드포인트 | 설명 | 비고 |
|---|-----------|------|------|
| 4 | `POST /yeopjeon/qr/cancel` | 사용자 QR 직접 취소 | 현재 5분 자동만료만 있음. STORY-15 연계 |
| 5 | `POST /yeopjeon/qr/merchant/reissue` | QR 재발급 | 분실·도용 의심 시 기존 QR 무효화 후 재발급. STORY-12 연계 |
| 6 | `POST /payments/{orderId}/cancel` | 충전 요청 사용자 직접 취소 | PENDING 중 타임아웃 전 취소 |
| 13 | `PATCH /admin/shops/{shopId}/place` | 관리자 가게-장소 수동 연결 | shops.place_id 추가 후 구현. 자동 매칭 대안 (KAN-??-shop-place-link) |
| 11 | `GET /users/me/items/{itemId}/qr` | 아이템 QR 코드 조회 | 사용자가 "사용하기" 버튼 → JWT 서명 QR 토큰 발급(TTL 5분). 스크린샷 재사용 방지 |
| 12 | `POST /merchants/me/shop/items/use` | 아이템 사용 처리 (상인/직원 스캔) | 관광지 직원·상인이 QR 스캔 → 토큰 검증 → UserItem USED 전환 + used_at 기록. UNUSED 검증으로 이중 사용 차단 |

---

## ✅ 구현 완료 (이전 목록에서 제거)

| 엔드포인트                          | 비고                        |
|--------------------------------|---------------------------|
| `GET /payments/history`        | PaymentController 구현됨     |
| `GET /merchants/me/shop/wallet` | ShopWalletController 구현됨  |
| `PATCH /merchants/me/shop/account` | ShopAccountController (PUT) 구현됨 |
| `GET /yeopjeon/qr/status/{paymentRequestId}` | QrPayController (GET) 구현됨 |
| QR 결제 엽전 이력 `payRequestId` 추적  | `yeopjeon_histories`에 `pay_request_id` 컬럼 추가 → QR 결제 이력에서 payRequest 역추적 가능 |
| `POST /merchants/me/shops/{shopId}/notices` | 가게 공지 등록 — KAN-213 구현 완료 |
| `GET /merchants/me/shops/{shopId}/notices` | 가게 공지 목록 조회 — KAN-213 구현 완료 |
| `DELETE /merchants/me/shops/{shopId}/notices/{noticeId}` | 가게 공지 삭제 — KAN-213 구현 완료 |
| `PATCH /merchants/me/shops/{shopId}/status` | 상인 직접 상태 전환 (ACTIVE/CLOSED) — KAN-213 구현 완료 |
---

## 🔧 고도화 / 백로그 (API 아닌 작업 포함)

### Shop-Place 자동 매칭 (미구현)

**기능**: 상인 신청 좌표(lat, lng) → 가장 가까운 Place 자동 연결

**현재 불가 이유**:
- Place 테이블에 반경(radius) / 경계(polygon) 데이터 없음
- 시장은 점이 아닌 면적 → 중심점 거리 계산만으로 부정확
- "몇 미터 이내를 소속으로 볼지" 정책 미결정

**현재 대안**: 관리자가 `PATCH /admin/shops/{shopId}/place`로 placeId 수동 지정

**구현 조건**:
1. Place 테이블에 `radius_m` 또는 GeoJSON polygon 추가
2. 정책 결정 — "신청 좌표가 Place 반경 내 → 자동 매칭"
3. MySQL `ST_Distance_Sphere` / `ST_Contains` 쿼리로 구현 가능

**관련 이슈**: KAN-213 이후 별도 이슈 생성 필요

| # | 작업 | 설명 | 비고 |
|---|------|------|------|
| B2 | 일일 충전 총액 제한 | Redis `daily:charge:{userId}:{날짜}` 누적 금액 저장, 한도 초과 시 BusinessException | 한도 금액 미정, 지라 이슈 생성 필요 |
| B3 | 고아 PENDING 스케줄러 | 생성 후 10분↑ PENDING 주문 → PortOne API 상태 조회 → PAID면 COMPLETED+충전 / FAILED면 FAILED+멱등키 해제 | 웹훅 미도달·서버 크래시 복구용. `feature/KAN-??-pending-order-scheduler` |
| B7 | 오픈시간 기반 자동 상태전환 스케줄러 | `operatingHours`(String) 파싱 후 영업 종료 시간에 상태 자동 전환 | `operatingHours` 포맷 정규화 필요. 공수 있음 |
| B4 | cursor 페이징 공통화 리팩터 | `CursorPageResponse.of(raw, size, mapper, idExtractor)` static factory 추가. `StorePurchaseService`, `SettlementService`, `RefundService` 등 동일 5줄 패턴 통일 | `refactor/KAN-??-cursor-page-util` 별도 브랜치 |
| B5 | 상인 홈 대시보드 메트릭 확장 | 어제 vs 오늘 매출 비교, 시간대별 매출 분포, 결제 거절·실패 카운터 | PRD 확정 후 별도 STORY |
| B6 | 캐시 일관성 정책 ADR | `TTL 우선 + 도메인 액션 시 무효화` 패턴 통일. 무효화 트리거·실패 처리·Redis 장애 시 DB fallback 정책 | `docs/10_ADR.md` 대상 |

---

## 📐 설계 메모

### 아이템 사용 플로우 (11, 12번)

**흐름:**
```
사용자 "사용하기" 버튼
  → GET /users/me/items/{itemId}/qr
  → 서버: UserItem.status == UNUSED 검증 → JWT 서명 QR 토큰 발급 (TTL 5분)
  → 사용자 화면에 QR 표시

관광지 직원·상인 스캐너 앱으로 QR 스캔
  → POST /merchants/me/shop/items/use  { token: "..." }
  → 서버: JWT 검증(서명·만료) → UserItem 재조회 → status == UNUSED 확인
  → UserItem.status = USED, used_at = now(), used_shop_id = 스캐너 상인 shopId
  → 스캐너 화면: "입장 가능" / "이미 사용됨" / "만료됨" 구분 응답
```

**QR 토큰 포맷 (JWT):**
```json
{ "itemId": 601, "userId": 1001, "exp": 1720000000 }
```
- 서명 키: 기존 JWT_SECRET 재사용 또는 별도 시크릿
- TTL: 5분 (스크린샷 재사용 차단)

**DB 변경:**
- `user_items` 테이블에 컬럼 추가: `used_at DATETIME`, `used_shop_id BIGINT NULL`
- Flyway 마이그레이션 필요

**스캔 주체 권한:** `MERCHANT` role. 별도 직원 role은 MVP 범위 외.

**에러 응답 구분:**
| 케이스 | 에러코드 |
|---|---|
| 이미 사용됨 | `ITEM_ALREADY_USED` |
| QR 만료 | `ITEM_QR_EXPIRED` |
| 아이템 없음 | `ITEM_NOT_FOUND` |
| 본인 아이템 아님 | `ITEM_FORBIDDEN` |

---

### 상인 상태 전환 (10번)

**허용 전환:**
- `ACTIVE` ↔ `TEMPORARILY_CLOSED` (상인 자율)
- `SUSPENDED`, `CLOSED` 전환은 관리자 전용 유지

**ShopStatus에 `TEMPORARILY_CLOSED` 추가 필요** (현재: ACTIVE / SUSPENDED / CLOSED)
- Flyway ENUM 마이그레이션 필요

**엔드포인트:** `PATCH /merchants/me/shop/status`
```json
Request: { "status": "TEMPORARILY_CLOSED" }
```

---

## 📌 우선순위

```
1 (필수, 내일까지)
→ 2 → 3 (관리자 상세, 여유 시)
→ 4 ~ 9 (선택, 고도화)
→ B1 → B3 (추적성·안정성)
→ B2 → B4 → B5 → B6 (고도화)
```
