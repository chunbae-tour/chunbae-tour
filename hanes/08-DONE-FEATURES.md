# 08. 완료된 기능 목록

> 작업 완료 시 이 파일에 추가한다.
> **새 세션 시작 시 반드시 읽어서 중복 작업을 방지한다.**

---

## 완료된 기능

| 완료일 | Story | 기능 | 브랜치 | 핵심 파일 | 비고 |
|--------|-------|------|--------|-----------|------|
| 2026-05-19 | STORY-01 | Redis/Redisson 설정 + 도메인 ErrorCode 등록 | feature/KAN-45 | `RedisConfig.java`, `ErrorCode.java` | PAY/STORE/MERCHANT/SHOP 코드 추가, Redis password 적용 |
| 2026-05-19 | STORY-02 | Wallet 엔티티 + 엽전 잔액 조회 API | feature/KAN-48 | `domain/yeopjeon/` 전체 | GET /api/v1/yeopjeon/me, AFTER_COMMIT 이벤트, 동시성 처리 |
| 2026-05-20 | STORY-03 | 엽전 충전 요청 → 포트원 V2 사전등록 | feature/KAN-50 | `domain/payment/` 전체 | POST /api/v1/payments/charge, 멱등성 키, 포트원 V2 실연동, PaymentOrder ERD 정합 |
| 2026-05-20 | STORY-04 | PG 콜백 처리 → 잔액 증가 | feature/KAN-50 | `CallbackService`, `WalletService`, `WebhookVerifier` | POST /api/v1/payments/webhook, HMAC-SHA256 서명 검증, 비관적 락, YeopjeonHistory 기록 |
| 2026-05-21 | STORY-05 | 엽전 이력 조회 API | feature/KAN-79 | `YeopjeonHistoryService`, `YeopjeonHistoryRepository`, `YeopjeonHistoryResponse`, `YeopjeonController` | GET /api/v1/yeopjeon/histories, cursor keyset 페이징, 보안 통합 테스트 추가, ofCharge 팩토리 버그 수정 |
| 2026-05-21 | STORY-06 | 환불 요청 API | feature/KAN-81 | `Refund`, `RefundRepository`, `RefundService`, `RefundStatus`, `RefundRequest`, `RefundResponse` | POST /api/v1/payments/{orderId}/refund, 7일 기간 제한, 중복 방지(PAY_007), 실 PG 환불은 STORY-07에서 처리 |
| 2026-05-21 | STORY-07 | 관리자 환불 승인/거절 API | feature/KAN-87 | `AdminRefundService`, `AdminRefundController`, `RefundDetailResponse`, `WalletService.reclaimForRefund()`, `Refund.rejectReason`, `PaymentOrder.refund()`, `PaymentOrderStatus.REFUNDED`, `RefundRejectRequest` | GET /api/v1/admin/refunds (cursor 페이징), PATCH /approve, PATCH /reject(거절사유), 단일 트랜잭션(PG 포함), 락 순서: Refund→PaymentOrder→Wallet |
| 2026-05-22 | STORY-08 | 상인 등록 신청 API | feature/KAN-101 | `MerchantApplication`, `MerchantApplicationService`, `MerchantApplicationRepository`, `MerchantApplicationResponse`, `MerchantApplyRequest` | POST /api/v1/merchants/apply, NTS 체크섬 검증, uk_merchant_applications_business_number 유니크 제약, MERCHANT_004 추가 |
| 2026-05-23 | STORY-09 | 관리자 상인 승인/거절 API | feature/KAN-102 | `AdminMerchantApplicationService`, `AdminMerchantApplicationController`, `Shop`, `ShopRepository`, `MerchantApplicationDetailResponse` | POST /approve·/reject, GET /applications (cursor 페이징), 비관적 락, uk_shops_application_id 유니크 제약(1신청→1가게), Account.promoteToMerchant() 추가(멱등: MERCHANT이면 skip, ADMIN이면 예외), MerchantApplication.approve()에서 activeFlag=null(uk_merchant_active_user_id 해제 → 추가 가게 신청 가능) |
| 2026-05-24 | STORY-10 | 상인 가게 조회/수정 API | feature/KAN-107 | `ShopController`, `ShopService`, `ShopResponse`, `ShopUpdateRequest`, `ShopRepository`, `ShopStatus`, `Shop` | GET /merchants/me/shops (목록), GET/PATCH /merchants/me/shops/{shopId}, ACTIVE 상태 가드(SHOP_005), rating double, applicationId UK, imageUrls @Size(max=2000) |
| 2026-05-24 | STORY-11 | 메뉴 CRUD API | feature/KAN-108 | `MenuController`, `MenuService`, `MenuRepository`, `Menu`, `MenuCreateRequest`, `MenuUpdateRequest`, `MenuResponse` | POST/PATCH/DELETE /merchants/me/shops/{shopId}/menus, ACTIVE 가드(SHOP_005), soft delete(@SQLRestriction), 소유권 검증(findByIdAndShopId→SHOP_004), price @Min(1) |
| 2026-05-24 | STORY-12 | 가게 공개 조회 + QR 코드 발급 API | feature/KAN-110 | `ShopInfoController`, `ShopInfoResponse`, `QrController`, `QrService` | GET /shops/{shopId} (공개), GET /merchants/me/shops/{shopId}/qr (MERCHANT), rating/reviewCount/isCertified 포함, ShopInfoResponse vs ShopResponse 구분 Javadoc |
| 2026-05-25 | STORY-13 | QR 결제 요청 생성 API | feature/KAN-112 | `QrPayController`, `QrPayService`, `QrPayRequest`, `QrPayRequestRepository`, `QrPayCreateRequest`, `QrPayItemRequest`, `QrPayCreateResponse`, `QrPayStatus` | POST /payments/qr, 메뉴 스냅샷 JSON 저장, Clock 주입(testable time), 중복 PENDING 차단(PAY_022), 자가결제 차단(PAY_023), 0원 차단(PAY_024), 잔액 사전 체크, 14개 테스트 |
| 2026-05-25 | KAN-115 | 사용자 환불 내역 조회 API | feature/KAN-115 | `UserRefundResponse`, `RefundService`, `RefundRepository`, `PaymentController`, `CursorUtils` | GET /payments/refunds, status 필터 + cursor 페이징, RefundRepository 4→1(findByUserIdWithFilter), AdminRefundService 2→1(findWithCursor), CursorUtils.decodeSafe() 공통화, RefundResponse.createdAt 추가 |
| 2026-05-27 | STORY-14 | QR 결제 승인/거절 API | feature/KAN-135-qr-pay-confirm | `QrPayService.confirmQrPayRequest()`, `QrPayRequestRepository.findByPayRequestIdWithLock()`, `ShopWallet`, `ShopWalletRepository`, `QrPayConfirmRequest`, `QrPayConfirmRequest.Action` | PATCH /api/v1/payments/qr/{payRequestId}/confirm, 분산 락 + 락-이후-재조회(비관적 락), 락 순서 wallets→shop_wallets(데드락 방지), REJECT도 락 이후 처리, MERCHANT 권한 가드, 8건 테스트 |
| 2026-05-27 | STORY-15 | QR 결제 만료 스케줄러 | feature/KAN-145-qr-pay-expiry-scheduler | `QrPayExpiryScheduler` | 5분 주기 PENDING→EXPIRED 일괄 전이, @Scheduled(fixedDelay) |
| 2026-05-27 | STORY-16 | 스토어 상품 목록/상세 조회 API | feature/KAN-150-store-product-query | `ProductController`, `ProductService`, `ProductRepository`, `Product`, `ProductStatus`, `ProductSummaryResponse`, `ProductDetailResponse` | GET /api/v1/store/products (cursor 페이징), GET /api/v1/store/products/{productId} (Redis 5분 캐시 + DB fallback), HIDDEN 차단, 화이트리스트 쿼리(ON_SALE·SOLD_OUT IN), category blank normalize, 공개 API(비인증), 8건 테스트 |
| 2026-05-27 | STORY-17 | 스토어 상품 구매 API | feature/KAN-158-store-purchase | `StoreOrderController`, `StorePurchaseService`, `StoreOrder`, `UserItem`, `StoreOrderRepository`, `UserItemRepository`, `StorePurchaseRequest`, `StoreOrderResponse`, `StoreOrderStatus`, `UserItemStatus`, `WalletService.spendForPurchase()`, `ProductRepository.findByIdWithLock()`, `Product.decreaseStock()` | POST /api/v1/store/orders (구매), GET /api/v1/store/orders (내 주문 내역, cursor 페이징), 3단계 동시성(Redis DECR→Redisson 분산 락→DB SELECT FOR UPDATE), 재고 0→SOLD_OUT 자동 전환, Redis 키 미세팅 시 DB 폴백, 실패 시 Redis 재고 복구(finally), 락 순서 Product→Wallet, UserItem 수량만큼 batch INSERT(saveAll), 구매 성공 시 product 캐시 무효화, 12건 테스트 |
| 2026-05-28 | STORY-19 | 정산 신청 + 관리자 처리 API | feature/KAN-160-settlement | `SettlementController`, `AdminSettlementController`, `SettlementService`, `AdminSettlementService`, `Settlement`, `SettlementStatus`, `SettlementRepository` | POST/GET /api/v1/merchants/me/shops/{shopId}/settlements, GET /api/v1/admin/settlements(?status 필터), PATCH /approve·/reject, 최소 5,000엽전 제한, 중복 PENDING 차단, 거절 사유 필수(@NotBlank), 계좌 스냅샷, 계좌번호 digit-index 마스킹(하이픈 유지), 비관적 락(락 순서 ShopWallet→Settlement, approve는 비잠금 peek→ShopWallet 락→Settlement 락 2단계), SHOP_008~012 에러코드 추가, 14건 테스트 |

---

## 구현 완료 상태 요약

### domain/yeopjeon (STORY-02)
- `entity/Wallet.java` — wallets 테이블, BaseEntity 상속
- `repository/WalletRepository.java`
- `service/WalletService.java` — saveAndFlush + DataIntegrityViolationException 동시성 처리
- `controller/YeopjeonController.java` — GET /api/v1/yeopjeon/me
- `dto/response/WalletBalanceResponse.java`
- `event/WalletEventListener.java` — @TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW

### domain/payment (STORY-03 + STORY-04)
- `entity/PaymentOrder.java` — id(BIGINT), orderUid(UUID), idempotencyKey, paymentMethod, pgTransactionId 포함 ERD 정합
- `repository/PaymentOrderRepository.java` — findByOrderUidWithLock (PESSIMISTIC_WRITE)
- `service/ChargeService.java` — 금액 검증(PAY_002/003/004), MIN 5,000원
- `service/IdempotencyService.java` — Redis setIfAbsent TTL 24h, DB 영구 저장
- `service/CallbackService.java` — handleSuccess/handleFail, noRollbackFor, scheduleUnmark(afterCommit)
- `service/WebhookVerifier.java` — PortOne V2 HMAC-SHA256 서명 검증, timestamp replay 방지
- `controller/PaymentController.java` — POST /api/v1/payments/charge + /webhook
- `client/PaymentGatewayClient.java` — 인터페이스 (preRegister + verifyPayment)
- `client/PortOnePaymentGatewayClient.java` — 포트원 V2 pre-register + GET /payments/{id}
- `config/PortOneProperties.java` — secret, webhookSecret, storeId, baseUrl, 채널키 바인딩
- `config/PortOneConfig.java` — RestClient 빈 등록
- `dto/request/ChargeRequest.java` — @NotNull(paymentMethod), @Min(5000), @Max(100000)
- `dto/request/WebhookPayload.java` — type, data(paymentId, txId)
- `dto/response/ChargeResponse.java` — orderUid 반환 (V2 SDK 방식, redirectUrl 없음)
- `type/PaymentOrderStatus.java` — PENDING/COMPLETED/FAILED/CANCELLED
- `type/PaymentMethod.java` — CARD/KAKAO_PAY/TOSS_PAY/FOREIGN_CARD

### domain/yeopjeon (STORY-04 추가)
- `entity/YeopjeonHistory.java` — yeopjeon_histories 테이블, CHARGE/SPEND/REFUND 타입, balanceSnapshot
- `repository/YeopjeonHistoryRepository.java`
- `service/WalletService.java` — charge() 추가: findByUserIdWithLock + credit + YeopjeonHistory 저장
- `repository/WalletRepository.java` — findByUserIdWithLock (PESSIMISTIC_WRITE) 추가
- `type/YeopjeonHistoryType.java` — CHARGE/SPEND/REFUND enum

### domain/yeopjeon (STORY-05 추가)
- `service/YeopjeonHistoryService.java` — cursor keyset 페이징, userId 소유권 검증
- `controller/YeopjeonController.java` — GET /api/v1/yeopjeon/histories 추가
- `dto/response/YeopjeonHistoryResponse.java`
- `repository/YeopjeonHistoryRepository.java` — findByUserIdOrderByIdDesc, findByUserIdAndIdLessThanOrderByIdDesc

### domain/payment (STORY-06 추가)
- `entity/Refund.java` — refunds 테이블, PENDING/APPROVED/REJECTED/CANCELLED, rejectReason
- `repository/RefundRepository.java` — findByIdWithLock (PESSIMISTIC_WRITE), existsByPaymentOrderIdAndStatusIn
- `service/RefundService.java` — 7일 기간 제한, 중복 신청 방지(PAY_016), 사용자 취소
- `controller/RefundController.java` — POST /api/v1/payments/{orderId}/refund, DELETE /{refundId}
- `dto/request/RefundRequest.java`
- `dto/response/RefundResponse.java`
- `type/RefundStatus.java` — PENDING/APPROVED/REJECTED/CANCELLED

### domain/payment (STORY-07 추가)
- `service/AdminRefundService.java` — 단일 트랜잭션(PG 포함), cursor 페이징, size 검증(PAY_021), 락 순서: Refund→PaymentOrder→Wallet
- `controller/AdminRefundController.java` — GET /api/v1/admin/refunds, PATCH /approve, PATCH /reject
- `dto/request/RefundRejectRequest.java`
- `dto/response/RefundDetailResponse.java` — rejectReason 포함
- `client/PaymentGatewayClient.java` — cancelPayment() 추가
- `client/PortOnePaymentGatewayClient.java` — cancelPayment() 구현
- `entity/PaymentOrder.java` — refund() 메서드, REFUNDED 상태 전이
- `type/PaymentOrderStatus.java` — REFUNDED 추가
- `service/WalletService.java` — reclaimForRefund() 추가 (엽전 회수 + REFUND 이력)

### domain/merchant (STORY-08)
- `entity/MerchantApplication.java` — merchant_applications 테이블, uk_merchant_applications_business_number 유니크 제약, approve()/reject() 상태 전이
- `repository/MerchantApplicationRepository.java` — existsByUserIdAndStatusIn
- `service/MerchantApplicationService.java` — NTS 체크섬 검증, 중복 신청 차단, DataIntegrityViolationException 필터링
- `controller/MerchantApplicationController.java` — POST /api/v1/merchants/apply
- `dto/request/MerchantApplyRequest.java`
- `dto/response/MerchantApplicationResponse.java`
- `type/MerchantApplicationStatus.java` — PENDING/APPROVED/REJECTED

### domain/merchant (STORY-09 추가)
- `service/AdminMerchantApplicationService.java` — 승인/거절/목록 조회, 비관적 락, cursor keyset 페이징, 락 순서: MerchantApplication→Account. 승인 시 명시적 role 가드 없음 — promoteToMerchant() 내부에서 ADMIN 예외 처리
- `controller/AdminMerchantApplicationController.java` — POST /api/v1/admin/merchants/{id}/approve, POST /reject, GET /applications
- `dto/request/MerchantApplicationDecisionRequest.java`
- `dto/response/MerchantApplicationDetailResponse.java`
- `repository/MerchantApplicationRepository.java` — findByStatusOrderByIdDesc, findByStatusAndIdLessThanOrderByIdDesc, findByIdWithLock (PESSIMISTIC_WRITE), existsByBusinessNumberAndStatusIn, existsByUserIdAndStatusIn 추가

### domain/merchant (KAN-160 다중 가게 지원 변경)
- `entity/MerchantApplication.java` — approve()에서 activeFlag=null 추가: uk_merchant_active_user_id 유니크 제약 해제 → 승인 후 동일 상인이 추가 가게 재신청 가능
- `service/MerchantApplicationService.java` — 사용자 중복 체크: ACTIVE_STATUSES(PENDING+APPROVED) → USER_PENDING_STATUSES(PENDING only). 사업자번호 중복 체크: BIZ_ACTIVE_STATUSES(PENDING+APPROVED) 유지
- `config/SecurityConfig.java` — POST /merchants/apply: hasRole("USER") → hasAnyRole("USER", "MERCHANT"). 1상인 다중 가게 지원
- `entity/Account.java` — promoteToMerchant(): role==MERCHANT이면 멱등 skip(추가 가게 승인 시 재호출 대비). USER 이외(ADMIN 등) 예외는 유지

### domain/shop (STORY-19 추가)
- `entity/Settlement.java` — settlements 테이블, shop_id 인덱스, 계좌 스냅샷(신청 시점), approve()/reject() 상태 전이
- `type/SettlementStatus.java` — PENDING/APPROVED/REJECTED
- `repository/SettlementRepository.java` — findByIdWithLock(비관적 락), findByShopId(cursor), findAllWithCursor(관리자, status=null이면 전체 조회)
- `service/SettlementService.java` — 신청(잔액>0 + 최소 5,000엽전 + 중복 PENDING 차단), 내 내역 조회 (userId + shopId 소유권 검증)
- `service/AdminSettlementService.java` — 승인(ShopWallet 차감), 거절, 목록 조회(?status 필터). 락 순서: ShopWallet→Settlement. approve는 비잠금 peek(shopId 취득) → ShopWallet SELECT FOR UPDATE → Settlement SELECT FOR UPDATE 2단계
- `controller/SettlementController.java` — POST/GET /api/v1/merchants/me/shops/{shopId}/settlements
- `controller/AdminSettlementController.java` — GET /api/v1/admin/settlements(?status), PATCH(approve/reject)
- `dto/request/SettlementRejectRequest.java` — `@NotBlank @Size(max=500)` reason (거절 사유 필수)
- `dto/response/SettlementResponse.java` — shopId 포함, 계좌번호 digit-index 마스킹(하이픈 보존, 앞 3자리·뒤 4자리 노출)
- `dto/response/AdminSettlementResponse.java` — 계좌번호 평문(관리자 수동 이체용), 마스킹 미적용 사유 Javadoc 명시
- `ErrorCode` — SHOP_008~012 추가 (SETTLEMENT_NOT_FOUND, DUPLICATE_SETTLEMENT_REQUEST, SETTLEMENT_INVALID_STATUS, SETTLEMENT_BALANCE_EMPTY, SETTLEMENT_AMOUNT_TOO_LOW)

### domain/shop (STORY-09 신규)
- `entity/Shop.java` — shops 테이블, uk_shops_application_id 유니크 제약(1신청→1가게), uk_shops_user_id 제약 없음(1상인 다중 가게 허용)
- `repository/ShopRepository.java` — findAllByUserId(목록), findByIdAndUserId(소유권 검증)

### domain/shop (STORY-11 추가)
- `entity/Menu.java` — menus 테이블, `@SQLRestriction("deleted_at IS NULL")` soft delete, `softDelete()` / `update()` 메서드
- `repository/MenuRepository.java` — `findByIdAndShopId` (소유권 검증)
- `service/MenuService.java` — 등록/수정/삭제, ACTIVE 가드, soft delete
- `controller/MenuController.java` — POST 201 / PATCH 200 / DELETE 204
- `dto/request/MenuCreateRequest.java` — `@NotBlank` name, `@NotNull @Min(1)` price
- `dto/request/MenuUpdateRequest.java` — 부분 수정, null=수정안함
- `dto/response/MenuResponse.java`

### domain/shop (STORY-12 추가)
- `dto/response/ShopInfoResponse.java` — 공개 뷰 (rating, reviewCount, isCertified, menus 포함, userId·imageUrls·status 제외), ShopResponse와 구분 Javadoc
- `controller/ShopInfoController.java` — GET /api/v1/shops/{shopId} (비인증 공개)

### domain/payment (KAN-115 추가)
- `dto/response/UserRefundResponse.java` — 사용자용 환불 내역 응답 DTO (관리자용 RefundDetailResponse와 구분)
- `repository/RefundRepository.java` — `findByUserIdWithFilter` JPQL — status/cursorId null이면 조건 미적용, 4가지 조합을 쿼리 1개로 처리. `findWithCursor` — 관리자 목록 조회, cursorId null이면 전체 첫 페이지
- `service/RefundService.java` — `getUserRefundHistory()` 추가 (status 필터 + cursor 페이징, size 1~100 검증)
- `controller/PaymentController.java` — `GET /api/v1/payments/refunds` 추가
- `dto/response/RefundResponse.java` — `createdAt` 추가 (클라이언트 7일 만료 기준 표시용)
- `domain/common/util/CursorUtils.java` — `decodeSafe()` 추가 — null→null, 유효하지 않은 cursor→INVALID_CURSOR. RefundService·AdminRefundService 중복 private 메서드 대체

### domain/payment (STORY-14 추가)
- `service/QrPayService.java` — `confirmQrPayRequest()`: 분산 락(Redisson) + 락-이후-재조회(findByPayRequestIdWithLock), REJECT 경로 락 이후 처리, 락 순서 wallets→shop_wallets
- `repository/QrPayRequestRepository.java` — `findByPayRequestIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`) 추가
- `entity/ShopWallet.java` — shop_wallets 테이블, credit() 메서드
- `repository/ShopWalletRepository.java` — `findByShopIdWithLock` (PESSIMISTIC_WRITE) 추가
- `controller/QrPayController.java` — PATCH /api/v1/payments/qr/{payRequestId}/confirm 추가
- `dto/request/QrPayConfirmRequest.java` — `Action(APPROVE/REJECT)`, `rejectReason`
- `type/QrPayStatus.java` — COMPLETED/REJECTED 전이 추가

### domain/payment (STORY-15 추가)
- `service/QrPayExpiryScheduler.java` — @Scheduled(fixedDelay=5분), PENDING → EXPIRED 일괄 전이

### domain/store (STORY-17 추가)
- `entity/StoreOrder.java` — store_orders 테이블, (user_id, id) 복합 인덱스(cursor 페이징), 구매 시점 스냅샷(productName, productPrice), `StoreOrder.create()` 팩토리
- `entity/UserItem.java` — user_items 테이블, idx_user_item_user_id 인덱스, `expiresAt`(ZoneId.of("Asia/Seoul") 기반), `UserItem.create()` 팩토리
- `type/StoreOrderStatus.java` — COMPLETED/CANCELLED
- `type/UserItemStatus.java` — AVAILABLE/USED/EXPIRED
- `repository/StoreOrderRepository.java` — `findByUserId` JPQL(cursor keyset, id DESC)
- `repository/UserItemRepository.java` — JpaRepository 최소 구현
- `service/StorePurchaseService.java` — 3단계 동시성, Redis hasKey skip, `redisDecremented` 플래그로 finally 복구
- `controller/StoreOrderController.java` — POST /api/v1/store/orders (201 Created), GET /api/v1/store/orders
- `dto/request/StorePurchaseRequest.java` — `@NotNull Long productId`, `@NotNull @Min(1) @Max(99) Integer quantity`
- `dto/response/StoreOrderResponse.java` — `StoreOrderResponse.from(StoreOrder)` 팩토리
- `entity/Product.java` — `decreaseStock(int quantity)` 추가 (재고 0→SOLD_OUT 자동 전환)
- `repository/ProductRepository.java` — `findByIdWithLock` (@Lock PESSIMISTIC_WRITE) 추가
- `service/WalletService.java` — `spendForPurchase(Long userId, long amount, String productName)` 추가

### domain/store (STORY-16 신규)
- `entity/Product.java` — products 테이블, BaseEntity 상속, imageUrls(JSON), merchantName, stock/originalStock, validityDays
- `type/ProductStatus.java` — ON_SALE/SOLD_OUT/HIDDEN
- `repository/ProductRepository.java` — `findVisibleProducts` (IN 화이트리스트, category null=전체, cursor keyset 페이징)
- `service/ProductService.java` — 목록(cursor 페이징, category normalize), 상세(Redis 5분 캐시+DB fallback+HIDDEN 차단), parseImageUrls null/blank 필터
- `controller/ProductController.java` — GET /api/v1/store/products, GET /api/v1/store/products/{productId} (공개 API, 비인증)
- `dto/response/ProductSummaryResponse.java` — 경량 응답 (첫 이미지만, soldCount, ProductStatus)
- `dto/response/ProductDetailResponse.java` — 풀 응답 (전체 이미지, description, validityDays, ProductStatus)

### domain/payment (STORY-13 추가)
- `entity/QrPayRequest.java` — qr_pay_requests 테이블, payRequestId(UUID), menuItems(JSON 스냅샷), expiredAt, rejectReason, QrPayStatus
- `repository/QrPayRequestRepository.java` — findByPayRequestId, existsByUserIdAndShopIdAndStatus
- `service/QrPayService.java` — 메뉴 스냅샷 구성, 중복 menuId 검증, ACTIVE 가드, 자가결제 차단, 중복 PENDING 차단, 0원 차단, 잔액 사전 체크, Clock 주입
- `controller/QrPayController.java` — POST /api/v1/payments/qr
- `dto/request/QrPayCreateRequest.java` — @Size(max=50) @Valid menuItems
- `dto/request/QrPayItemRequest.java` — @Min(1) @Max(999) quantity
- `dto/response/QrPayCreateResponse.java` — payRequestId, shopId, shopName, totalAmount, menuItems(스냅샷), expiredAt
- `type/QrPayStatus.java` — PENDING/COMPLETED/REJECTED/EXPIRED

### domain/shop (STORY-10 추가)
- `entity/Shop.java` — 풀 ERD (imageUrls JSON, operatingHours, closedDays, isCertified, rating double, reviewCount, status), uk_shops_application_id UK, update() 메서드
- `type/ShopStatus.java` — ACTIVE/SUSPENDED/CLOSED
- `repository/ShopRepository.java` — findAllByUserId, findByIdAndUserId 추가
- `service/ShopService.java` — getMyShops(목록, SUSPENDED/CLOSED 포함), getMyShop(단건, shopId 소유권 검증), updateMyShop(shopId, ACTIVE 상태 가드 SHOP_005)
- `controller/ShopController.java` — GET /api/v1/merchants/me/shops (목록), GET/PATCH /api/v1/merchants/me/shops/{shopId}
- `dto/request/ShopUpdateRequest.java` — null=수정안함, shopName/category @Size(min=1,max=50), imageUrls @Size(max=2000) JSON 합산
- `dto/response/ShopResponse.java` — 전체 ERD 필드 포함
- `domain/common/error/ErrorCode.java` — SHOP_005(SHOP_INACTIVE) 추가

### domain/auth (STORY-09 타 도메인 수정)
- `entity/Account.java` — promoteToMerchant() 추가 (USER → MERCHANT 권한 상승)
- `repository/AccountRepository.java` — findByIdWithLock (PESSIMISTIC_WRITE) 추가

### domain/common (STORY-01/02)
- `entity/BaseEntity.java` — createdAt/updatedAt 공통 추상 클래스
- `error/ErrorCode.java` — PAY_001~024, STORE_001~007, MERCHANT_001~005, SHOP_001~006
- `config/RedisConfig.java` — RedissonClient (password 지원), StringRedisTemplate, RedisTemplate

---

## 다음 구현 대상

| Story | 기능 | 브랜치 예정 |
|-------|------|------------|
| ~~STORY-09~~ | ~~관리자 상인 승인/거절~~ | ~~feature/KAN-102~~ | ✅ 완료 |

---

## TODO (별도 STORY 예정)

| 기능 | 내용 | 비고 |
|------|------|------|
| 일일 충전 총액 제한 | Redis `daily:charge:{userId}:{날짜}` 누적 금액 저장, 한도 초과 시 BusinessException | 한도 금액 미정, 지라 이슈 생성 필요 |
| cursor 페이징 공통화 리팩터 | `CursorPageResponse.of(raw, size, mapper, idExtractor)` static factory 추가 → 모든 서비스의 5줄 cursor 빌딩 패턴을 1줄로 통일. 현재 `StorePurchaseService`, `SettlementService`, `AdminSettlementService`, `RefundService`, `AdminRefundService`, `YeopjeonHistoryService` 등 동일 패턴 반복 중 | 별도 `refactor/KAN-??-cursor-page-util` 브랜치 권장. KAN-160 스코프 초과로 분리 |
| 고아 PENDING 스케줄러 | 생성 후 10분↑ PENDING 주문 → PortOne API 상태 조회 → PAID면 COMPLETED+충전 / FAILED면 FAILED+멱등키 해제 | 브랜치: `feature/KAN-??-pending-order-scheduler`. 웹훅 미도달/서버 크래시 복구용 |
| 가게 공지 기능 | 상인이 임시 휴업/이벤트 등 공지를 올릴 수 있는 기능. 현재 `description`(가게 소개)에 공지를 섞으면 역할 분리 안 됨 — 기간/만료/이력 관리 불가. 별도 `shop_notices` 테이블 필요. **필요 필드:** `id(BIGINT PK)`, `shop_id(FK)`, `title(VARCHAR 100)`, `content(TEXT)`, `created_at`, `updated_at`. **API:** `POST /merchants/me/shop/notices`, `GET /merchants/me/shop/notices`, `DELETE /merchants/me/shop/notices/{id}`. **진행 시점:** 가게 기본 관리(STORY-10) 완료 후, 메뉴 관리 다음 우선순위 권장. | 지라 이슈 생성 필요 |
| 상인 홈 대시보드 메트릭 확장 | 현재 범위는 `todaySalesAmount`와 최근 결제 10건. 후속 후보: 어제 vs 오늘 매출 비교/성장률, 시간대별 매출 분포, 결제 거절·실패 카운터(KAN-105 audit 연동) | PRD 확정 후 별도 STORY로 분리. API 응답 스키마, Repository 쿼리, 캐시 영향 범위 함께 설계 필요 |
| 캐시 일관성 정책 ADR | Product(PR #211), MerchantHome, 후속 도메인에서 `TTL 우선 + 도메인 액션 시 무효화` 패턴을 통일. 무효화 트리거, 실패 처리, TTL 기준, Redis 장애 시 DB fallback 정책 정리 | `docs/10_ADR.md` 대상. 대시보드 메트릭 확장과 별도 슬라이스 권장 |

---

## 미구현 API (코드리뷰 식별)

### 필수

| API | 설명 | 관련 STORY |
|-----|------|-----------|
| `GET /yeopjeon/qr/status/{paymentRequestId}` | QR 결제 상태 폴링. 푸시 미도달 시 사용자 확인 수단 없음 | STORY-13 또는 STORY-14 작업 시 함께 구현 |

### 선택 (시간되면)

| API | 설명 | 관련 STORY |
|-----|------|-----------|
| `POST /yeopjeon/qr/cancel` | 사용자 QR 직접 취소. 현재 5분 자동 만료만 있음 | STORY-15와 함께 |
| `POST /yeopjeon/qr/merchant/reissue` | QR 재발급 (분실/도용 의심 시). 기존 QR 무효화 필요 | STORY-12와 함께 |
| `POST /payments/{orderId}/cancel` | 충전 요청 후 타임아웃 전 사용자 직접 취소 | STORY-03 또는 별도 브랜치 |

### 가게 인증 심사 (shop-certifications)

> **주의**: `/admin/shop-certifications/**` 은 `/admin/merchant-applications/**` (상인 등록 신청)과 **다른 개념**.
>
> - `merchant-applications` → 사용자가 상인 자격 신청 → 승인 시 Account.role = MERCHANT
> - `shop-certifications` → 이미 상인인 사람이 **가게 인증 마크** 신청 → 승인 시 `shops.is_certified = true`
>
> `Shop.isCertified` 필드 및 `ShopInfoResponse`에 이미 노출 중 — DB/응답 설계는 완료, 컨트롤러/서비스 미구현.

| API | 설명 | 비고 |
|-----|------|------|
| `GET /admin/shop-certifications` | 인증 신청 목록 조회 | 미구현 |
| `GET /admin/shop-certifications/{applicationId}` | 인증 신청 상세 | 미구현 |
| `POST /admin/shop-certifications/{applicationId}/approve` | 인증 승인 → `shops.is_certified = true` | 미구현 |
| `POST /admin/shop-certifications/{applicationId}/reject` | 인증 거절 | 미구현 |
| `POST /admin/shops/{shopId}/certification/cancel` | 인증 취소 → `shops.is_certified = false` | 미구현 |
| `GET /admin/shops/{shopId}` | 관리자 가게 상세 조회 | 미구현 |
| `PATCH /admin/shops/{shopId}` | 관리자 가게 정보 수정 (status 변경 등) | 미구현 |

#### Swagger/OpenAPI 작성 시 누락 주의

- `/admin/shop-certifications/**` 는 상인 등록 승인 API가 아니라 **가게 인증 마크 심사 API**다.
- `Shop.isCertified`, `ShopResponse.isCertified`, `ShopInfoResponse.isCertified` 는 이미 존재하므로 Swagger 응답 스키마에는 노출될 수 있다.
- 하지만 인증 신청 엔티티/컨트롤러/서비스는 아직 없으므로, Swagger 도입 시 아래 둘을 분리해서 표시해야 한다.
  - 이미 구현됨: 가게 응답의 `isCertified` 필드
  - 미구현: 가게 인증 신청/승인/거절 관리자 API
- 후속 구현 시에는 상인 등록 신청(`MerchantApplication`)을 재사용하지 말고, 별도 `ShopCertificationApplication` 계열 모델을 두는 방향을 우선 검토한다.
