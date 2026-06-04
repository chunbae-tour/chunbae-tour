# 05. 내 도메인 에러코드

---

## COMMON (공통)

| ErrorCode Enum | Code | HTTP | 메시지 | 발생 상황 |
|----------------|------|------|--------|-----------|
| `INTERNAL_SERVER_ERROR` | COMMON_001 | 500 | 서버 오류가 발생했습니다. | 예상치 못한 서버 오류 |
| `INVALID_REQUEST` | COMMON_002 | 400 | 잘못된 요청입니다. | 요청 형식 오류 |
| `MISSING_REQUIRED_FIELD` | COMMON_003 | 400 | 필수 입력값이 누락되었습니다. | @NotNull 검증 실패 |
| `INVALID_INPUT_VALUE` | COMMON_004 | 400 | 입력값이 유효하지 않습니다. | @NotBlank/@Min/@Max 등 검증 실패 |
| `RESOURCE_NOT_FOUND` | COMMON_005 | 404 | 요청한 리소스를 찾을 수 없습니다. | 일반 리소스 없음 |
| `TOO_MANY_REQUESTS` | COMMON_006 | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. | 요청 횟수 초과 |
| `EXTERNAL_SERVICE_ERROR` | COMMON_007 | 503 | 외부 서비스 연동 중 오류가 발생했습니다. | PG사 등 외부 API 오류 |
| `INVALID_CURSOR` | COMMON_008 | 400 | 유효하지 않은 커서 값입니다. | 커서 디코딩 실패 |
| `CONCURRENT_UPDATE` | COMMON_009 | 409 | 동시 수정 충돌이 발생했습니다. 다시 시도해주세요. | 낙관적 락 충돌 |
| `INVALID_PAGE_SIZE` | COMMON_010 | 400 | 페이지 크기는 1 이상 100 이하여야 합니다. | @Min(1) @Max(100) 검증 실패 |

---

## PAY (결제 / 엽전)

| ErrorCode Enum | Code | HTTP | 메시지 | 발생 상황 |
|----------------|------|------|--------|-----------|
| `INSUFFICIENT_BALANCE` | PAY_001 | 400 | 엽전 잔액이 부족합니다. | 잔액 < 결제 금액 |
| `CHARGE_AMOUNT_TOO_LOW` | PAY_002 | 400 | 충전 금액은 1,000원 이상이어야 합니다. | 최소 충전 금액 미달 |
| `INVALID_CHARGE_UNIT` | PAY_003 | 400 | 충전 금액은 1,000원 단위로 입력해주세요. | 충전 금액 단위 오류 |
| `CHARGE_AMOUNT_EXCEEDED` | PAY_004 | 400 | 1회 최대 충전 금액은 100,000원입니다. | 최대 충전 금액 초과 |
| `PAYMENT_SERVICE_UNAVAILABLE` | PAY_005 | 503 | 결제 서비스를 일시적으로 사용할 수 없습니다. | PG사 장애 |
| `PAYMENT_CANCELLED` | PAY_006 | 400 | 결제가 취소되었습니다. | 사용자 결제 취소 |
| `DUPLICATE_PAYMENT_REQUEST` | PAY_007 | 409 | 이미 처리된 결제 요청입니다. | 멱등성 키 중복 |
| `PAYMENT_PROCESSING` | PAY_008 | 503 | 결제 처리 중입니다. 잠시 후 다시 시도해주세요. | 분산 락 획득 실패 |
| `PAYMENT_HISTORY_NOT_FOUND` | PAY_009 | 404 | 존재하지 않는 결제 내역입니다. | 결제 ID 없음 |
| `REFUND_PERIOD_EXPIRED` | PAY_010 | 400 | 환불 가능한 기간이 지났습니다. | 환불 정책 기간 초과 |
| `PAYMENT_HISTORY_FORBIDDEN` | PAY_011 | 403 | 본인의 결제 내역만 조회할 수 있습니다. | 타인 결제 내역 조회 시도 |

---

## STORE (스토어 / 상품)

| ErrorCode Enum | Code | HTTP | 메시지 | 발생 상황 |
|----------------|------|------|--------|-----------|
| `PRODUCT_NOT_FOUND` | STORE_001 | 404 | 존재하지 않는 상품입니다. | 상품 ID 없음 |
| `PRODUCT_SOLD_OUT` | STORE_002 | 409 | 품절된 상품입니다. | 재고 0 구매 시도 |
| `INVALID_PURCHASE_QUANTITY` | STORE_003 | 400 | 구매 수량은 1개 이상이어야 합니다. | 수량 0 이하 요청 |
| `PURCHASE_QUANTITY_EXCEEDED` | STORE_004 | 400 | 1회 최대 구매 수량을 초과했습니다. | 최대 구매 수량 초과 |
| `PURCHASE_PROCESSING` | STORE_005 | 503 | 구매 처리 중입니다. 잠시 후 다시 시도해주세요. | 분산 락 획득 실패 |
| `ORDER_NOT_FOUND` | STORE_006 | 404 | 존재하지 않는 주문입니다. | 주문 ID 없음 |
| `ORDER_ALREADY_CANCELLED` | STORE_007 | 400 | 이미 취소된 주문입니다. | 취소 완료 주문 재취소 |

---

## MERCHANT (상인 계정 / 신청 / 권한)

| ErrorCode Enum | Code | HTTP | 메시지 | 발생 상황 |
|----------------|------|------|--------|-----------|
| `MERCHANT_CERT_ALREADY_PENDING` | MERCHANT_001 | 409 | 이미 상인 인증 신청이 진행 중입니다. | 중복 인증 신청 |
| `INVALID_BUSINESS_NUMBER` | MERCHANT_002 | 400 | 유효하지 않은 사업자등록번호입니다. | 사업자 번호 유효성 실패 |
| `MERCHANT_NOT_CERTIFIED` | MERCHANT_003 | 403 | 상인 인증이 필요합니다. | 미인증 상인 제한 기능 접근 |

---

## SHOP (가게 / 메뉴 / 정산)

| ErrorCode Enum | Code | HTTP | 메시지 | 발생 상황 |
|----------------|------|------|--------|-----------|
| `SHOP_NOT_FOUND` | SHOP_001 | 404 | 존재하지 않는 가게입니다. | 가게 ID 없음 |
| `SHOP_UPDATE_FORBIDDEN` | SHOP_002 | 403 | 본인 가게 정보만 수정할 수 있습니다. | 타인 가게 수정 시도 |
| `SHOP_ALREADY_EXISTS` | SHOP_003 | 409 | 이미 등록된 가게가 있습니다. | uk_shops_application_id 중복 (동일 신청서로 가게 2개 생성 차단) |
| `MENU_NOT_FOUND` | SHOP_004 | 404 | 존재하지 않는 메뉴입니다. | 메뉴 ID 없음 |
| `SHOP_INACTIVE` | SHOP_005 | 403 | 정지 또는 폐업 상태의 가게입니다. | 정지/폐업 가게 수정·메뉴 등록·QR 결제 시도 |
| `MENU_DUPLICATE` | SHOP_006 | 409 | 이미 동일한 이름의 메뉴가 존재합니다. | 동일 가게 내 메뉴명 중복 |
| `MENU_UNAVAILABLE` | SHOP_007 | 409 | 현재 주문할 수 없는 메뉴입니다. | QR 결제 요청 시 품절/비활성 메뉴 포함 |
| `SETTLEMENT_NOT_FOUND` | SHOP_008 | 404 | 존재하지 않는 정산 요청입니다. | 정산 ID 없음 |
| `DUPLICATE_SETTLEMENT_REQUEST` | SHOP_009 | 409 | 이미 처리 대기 중인 정산 요청이 있습니다. | PENDING 정산 존재 시 중복 신청 차단 |
| `SETTLEMENT_INVALID_STATUS` | SHOP_010 | 409 | 현재 상태에서는 처리할 수 없는 정산 요청입니다. | PENDING 아닌 건 승인/거절 시도 |
| `SETTLEMENT_BALANCE_EMPTY` | SHOP_011 | 400 | 정산 가능한 잔액이 없습니다. | ShopWallet 잔액 0 |
| `SETTLEMENT_AMOUNT_TOO_LOW` | SHOP_012 | 400 | 정산 신청 최소 금액은 5,000엽전입니다. | 5,000엽전 미달 신청 |

---

## 사용 예시

```java
// 잔액 부족
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);

// 품절
throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);

// 분산 락 획득 실패
throw new BusinessException(ErrorCode.PURCHASE_PROCESSING);

// 멱등성 키 중복
throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
```
