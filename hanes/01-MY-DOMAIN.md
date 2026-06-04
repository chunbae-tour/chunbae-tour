# 01. 내 담당 도메인

## 담당자
- **이름**: 신현민
- **도메인**: 스토어 / 결제 / 상인 / 운영
- **핵심 기술 책임**: PG 연동, 분산 락, 동시성 제어

---

## ✅ 내가 담당하는 패키지 (수정 가능)

```
src/main/java/com/chunbaetour/
├── yeopjeon/       ← 엽전 지갑(Wallet), 잔액 조회 (13_백엔드_패키지_구조_설계서 기준)
├── payment/        ← 엽전 충전, PG 연동, 결제 내역, 환불
├── store/          ← 상품, 주문, 재고, 사용자 보유 아이템
├── merchant/       ← 상인 신청, 상인 권한, 사업자 인증
└── shop/           ← 가게 관리, 메뉴 관리, QR 결제, 정산, 광고

src/test/java/com/chunbaetour/
├── yeopjeon/
├── payment/
├── store/
├── merchant/
└── shop/
```

---

## 🗄️ 내가 담당하는 DB 테이블

| 테이블 | 도메인 |
|--------|--------|
| `wallets` | 결제 |
| `payment_orders` | 결제 |
| `yeopjeon_histories` | 결제 |
| `refund_requests` | 결제 |
| `qr_pay_requests` | 결제 |
| `products` | 스토어 |
| `store_orders` | 스토어 |
| `store_order_items` | 스토어 |
| `user_items` | 스토어 |
| `shops` | 상인/가게 |
| `menus` | 상인/가게 |
| `shop_wallets` | 상인/가게 |
| `settlement_requests` | 상인/가게 |
| `ad_applications` | 상인/가게 |
| `store_certifications` | 상인/가게 |
| `merchant_applications` | 상인 |

---

## 🔑 내가 담당하는 Redis 키

| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| `stock:{productId}` | 재고 가점유 | 10분 |
| `purchase:lock:{userId}` | 구매 분산 락 | 5초 |
| `payment:lock:{userId}` | 결제 분산 락 | 5초 |
| `qr:lock:{shopId}:{userId}` | QR 결제 분산 락 | 5초 |

---

## 🚫 절대 수정 금지 (다른 팀원 담당)

```
# 정민교 담당
com.chunbaetour.auth
com.chunbaetour.user
com.chunbaetour.admin

# 김인목 담당
com.chunbaetour.place
com.chunbaetour.search
com.chunbaetour.direction

# 박경화 담당
com.chunbaetour.community
com.chunbaetour.festival
com.chunbaetour.report
com.chunbaetour.like

# 임하은 담당
com.chunbaetour.chat
com.chunbaetour.matching
com.chunbaetour.notification
com.chunbaetour.translation
```

> 위 패키지는 읽기는 가능하지만 **수정은 절대 금지**한다.
> 연동이 필요한 경우 해당 서비스의 public 메서드만 호출한다.

---

## 🔗 다른 도메인과의 연동 지점

내 도메인에서 참조하는 외부 서비스:

| 참조 대상 | 사용 목적 | 담당자 |
|-----------|-----------|--------|
| `AccountRepository` | 사용자 존재 확인, 권한 확인 | 정민교 |
| `NotificationService` | 결제 완료 알림 (추후 확장) | 임하은 |

> **연동 시 주의**: 인터페이스/public 메서드만 사용. 내부 구현 수정 금지.
