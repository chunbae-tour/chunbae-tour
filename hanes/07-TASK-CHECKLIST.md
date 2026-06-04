# 07. 작업 체크리스트

---

## 작업 시작 전 체크리스트

- [ ] `01-MY-DOMAIN.md`는 패키지 경계가 헷갈릴 때만 확인
- [ ] `08-DONE-FEATURES.md`는 전체 읽기 금지 → STORY/KAN/기능명으로 검색해 이미 완료된 기능인지 확인
- [ ] 작업할 기능이 **내 도메인 범위** 안인지 확인
- [ ] 관련 문서를 `06-DOC-INDEX.md`에서 찾아 필요한 것만 읽기
- [ ] 동시성이 필요한 작업인지 확인 → `03-CONCURRENCY-GUIDE.md` 참조
- [ ] **브랜치 생성** → `CLAUDE.md` 브랜치 규칙 확인 후 지라 번호 물어보고 생성 (코드 수정 전 필수)

---

## 코드 작성 체크리스트

- [ ] 내 패키지(`payment`, `store`, `merchant`, `shop`) 안에서만 작업
- [ ] 다른 팀원 패키지 파일 수정하지 않음
- [ ] 공통 응답 Wrapper 형식 사용
- [ ] 에러코드는 `05-ERROR-CODES.md`에서 찾아 사용 (새로 만들지 말 것)
- [ ] 결제/구매 API에 멱등성 키 처리 포함
- [ ] 동시성 처리가 필요한 경우 `03-CONCURRENCY-GUIDE.md` 패턴 따름
- [ ] SQL 직접 작성 금지 → JPA/QueryDSL 사용
- [ ] 페이징은 Cursor 기반

---

## 작업 완료 후 체크리스트

### 1단계 — 테스트 실행 및 검증 (커밋 전 필수)

- [ ] 테스트 코드 작성 완료
- [ ] **테스트 실행**: `./gradlew test --tests "패키지.클래스명"` 으로 작성한 테스트 실행
- [ ] **테스트 결과 확인**: 모든 케이스 PASS 확인, FAIL 시 원인 파악 후 수정
- [ ] **테스트 `@DisplayName` 한글 작성**: 모든 테스트 메서드에 `@DisplayName`으로 한글 설명 필수
  ```java
  // 예시
  @Test
  @DisplayName("정상 충전 요청 시 PG 결제창 redirectUrl을 반환한다")
  void charge_success_returns_redirectUrl() { ... }

  @Test
  @DisplayName("동일 멱등성 키 재요청 시 PAY_007을 반환한다")
  void charge_duplicate_idempotency_throws_PAY_007() { ... }
  ```
- [ ] **앱 컴파일 확인**: `./gradlew compileJava` 로 빌드 에러 없는지 확인

### 2단계 — 커밋

- [ ] 변경된 파일 목록 요약 (Claude가 반드시 제공)
- [ ] **`08-DONE-FEATURES.md` 업데이트 필요 여부 확인** ← 기능 완료 기록이 필요한 경우만 수정
  - 완료일, Story 번호, 기능명, 브랜치, 핵심 파일, 비고 기록
  - 구현 완료 상태 요약 섹션도 갱신
- [ ] 다른 팀원 파일이 실수로 수정되지 않았는지 확인
- [ ] git add → commit (테스트 통과 확인 후에만)

---

## ⛔ git add 금지 목록

아래 경로는 **절대 `git add` 하지 않는다.** 팀 저장소에 올라가서는 안 되는 개인 전용 파일이다.

| 경로 | 이유 |
|------|------|
| `hanes/` | 에이전트 하네스 — 개인 전용 |
| `docs/` | 기획/설계 문서 — 개인 전용 |
| `.claude/` | Claude 설정 — 개인 전용 |
| `.agents/` | 에이전트 설정 — 개인 전용 |
| `skills-lock.json` | 스킬 잠금 파일 — 개인 전용 |

커밋 대상: **`src/`** 아래 소스코드만.

---

## 동시성 작업 추가 체크리스트

- [ ] Redis 가점유 복구 로직 포함 (실패 시 INCR)
- [ ] `finally` 블록에서 락 해제 보장
- [ ] 락 획득 실패 시 적절한 에러코드 반환
- [ ] 멱등성 키로 중복 요청 방지

---

## ⚠️ 미구현 API 목록 (2026-05-29 확인)

API 명세서에는 존재하지만 코드에 구현되지 않은 엔드포인트.

| API | 설명 | 관련 엔티티 | 비고 |
|-----|------|-------------|------|
| `GET /api/v1/payments/history` | PG 결제(엽전 충전) 주문 내역 조회 | `PaymentOrder` | `PaymentOrderRepository` 존재, cursor 페이징 필요 |
| `GET /api/v1/merchants/me/shop/wallet` | 상인 가게 수익 지갑 잔액 조회 | `ShopWallet` | `ShopWalletRepository.findByShopId()` 존재 |

> `/api/v1/yeopjeon/me` (엽전 잔액)·`/api/v1/yeopjeon/histories` (엽전 사용내역)과 **다른 API**임에 주의.
> - `payments/history` → PG로 결제한 충전 주문 이력 (PaymentOrder 테이블)
> - `merchants/me/shop/wallet` → QR 결제로 적립된 가게 수익 잔액 (ShopWallet 테이블, 사용자 Wallet과 별개)
