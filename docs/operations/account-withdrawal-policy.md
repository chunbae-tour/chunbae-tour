# 회원 탈퇴 cascade / 데이터 보존 / 재가입 정책 (KAN-144, Epic KAN-142 S2)

> 회원 탈퇴 시 부속 데이터(Wallet/UserLike) cascade 정책 + 동일 email 재가입 정책 + 운영 가이드.
> Epic C 마이그레이션: S1(KAN-143)이 soft delete + 토큰 cascade 무효화를 도입했고, S2가 본 정책 결정을 코드/문서로 확정.

## TL;DR

| 항목 | 결정 | 근거 요약 |
|---|---|---|
| 1. Wallet 보존 | ✅ 보존 (A안) | 환불/세무 대응, FK 없음, `@SQLRestriction`이 Account 조회만 제외 |
| 2. UserLike 삭제 | ✅ 즉시 hard delete (B안) | 보존 가치 낮은 개인 행동 데이터, `place.likeCount` 캐시로 통계 영향 0 |
| 3. 동일 email 재가입 | ❌ MVP 차단 (c안) | `email UNIQUE` 단순 인덱스 + MySQL 8.4 partial index 미지원 — 도입 비용 vs 운영 요구 빈도 |
| 4. ACCOUNT_DELETED audit | ✅ S2에서 발행 | KAN-105 표준 채널 + SIEM 추적성 |
| 5. 동시 탈퇴 race | ✅ CAS UPDATE | `markAsDeleted` atomic UPDATE — schema 변경 0, race-window 0, audit 정확히 1회 발행 보장 |

## §1. Wallet 보존 (A안)

### 결정
탈퇴 사용자의 `Wallet` row를 **보존**한다. cascade 삭제 없음.

### 근거
- **세무/환불 대응 요구**: 결제 도메인 운영 정책상 결제 이력은 최소 5년 보존이 표준. Wallet은 결제/충전 흐름의 잔액 기록 → 동일 보존 정책 필요.
- **FK 부재**: `Wallet`은 `Long userId` plain column + `uk_wallets_user_id` unique 제약만 사용. `@ManyToOne Account` 매핑이 없어 Hibernate가 FK를 생성하지 않음 → Account soft-delete가 Wallet에 영향 없음.
- **`@SQLRestriction` 효과**: 조회 흐름에서 탈퇴 Account는 자동 제외되지만 Wallet은 `userId`로 직접 조회되므로 정상 노출 (운영자/감사 흐름).
- **추후 hard delete 정책 변경 시**: 별도 데이터 보존 마이그레이션 (예: `wallets_archived` 테이블로 이관 후 row 삭제) — 본 슬라이스 범위 외.

### 구현 결과
- 코드 변경 없음. `UserMeService.deleteMe`에서 Wallet 조작 호출 없음.
- 통합 테스트: 탈퇴 후 `wallets` 테이블에 row 잔존 확인 (`AccountWithdrawalCascadeIntegrationTest`).

## §2. UserLike 즉시 hard delete (B안)

### 결정
탈퇴 사용자의 `user_likes` row를 **탈퇴 트랜잭션 내에서 일괄 hard delete**한다.

### 근거
- **개인 행동 데이터 — 보존 가치 낮음**: UserLike는 "어떤 사용자가 어떤 관광지를 찜했는지" 개인화 데이터. 운영/감사/세무 요구 없음.
- **통계 영향 0**: `Place` 엔티티가 `likeCount` 캐시 컬럼을 별도로 보유 (KAN-124 도입). UserLike row 삭제가 관광지 인기 통계에 영향을 주지 않음.
- **FK 위반 회피**: `UserLike`는 `@ManyToOne Account` + `@JoinColumn(name="user_id")` → Hibernate가 FK 생성. soft-delete만으로는 row 잔존 → 후속 통계 조회 시 stale 데이터 노출 위험. hard delete가 데이터 위생.
- **개인정보 최소 보존 원칙**: GDPR/PIPA 정신상 불필요한 개인 행동 기록은 즉시 삭제가 권장.

### 구현 결과
- `UserLikeRepository.deleteByUserId(Long)` 신규 — `@Modifying(flushAutomatically=true, clearAutomatically=true)`.
- `UserMeService.deleteMe`가 `AccountRepository.markAsDeleted` CAS UPDATE 성공(영향 row=1) **직후**에 호출. 같은 트랜잭션이므로 DB rollback 시 둘 다 원복.
- 삭제 row 수는 `ACCOUNT_DELETED` audit metadata `deletedLikes` 필드로 기록.

### 대안 (기각)
- **보존 (A안)**: place.likeCount 캐시가 이미 통계를 보유하므로 보존 가치가 0. 채택 사유 없음.

## §3. 동일 email 재가입 — MVP 차단 (c안)

### 결정
탈퇴 후 **동일 email로 재가입 불가**. 운영 요구 발생 시 별도 슬라이스로 partial unique index 도입.

### 근거
- **현재 schema 제약**: `Account.email`이 `@Column(unique=true)` — Hibernate가 단순 UNIQUE 인덱스 생성. `deletedAt`과 무관하게 email 중복 차단.
- **MySQL 8.4는 진짜 partial index 미지원**: PostgreSQL의 `CREATE UNIQUE INDEX ON users (email) WHERE deleted_at IS NULL` 같은 문법이 없음. 우회 방법:
  - **(a) Generated column 트릭**: `active_email = IF(deleted_at IS NULL, email, NULL)` STORED + UNIQUE. MySQL의 "NULL != NULL" 동작 이용. 동작은 정확하나 schema 변경 + 마이그레이션 도구(Flyway/Liquibase) 도입 필요.
  - **(b) 운영 raw SQL 마이그레이션**: `ddl-auto=validate` 운영에서 무중단 배포 절차 + 롤백 시나리오 문서화 필요.
  - **(c) 차단 유지**: 별도 마이그레이션 없이 현재 schema 그대로 — 채택.
- **운영 요구 빈도 추정**: 회원 탈퇴 후 동일 email 재가입은 MVP 단계에서 드문 시나리오. 사용자가 다른 email로 재가입 가능 (Gmail alias / 보조 메일).
- **비용 대비 효익**: (a)/(b)는 마이그레이션 도구 도입 + DBA 절차 정착 비용 발생. MVP에서 채택할 가치 낮음.

### 사용자 안내
- 클라이언트 회원가입 흐름에서 `AUTH_008` (DUPLICATE_EMAIL) 응답 시:
  - **표준 메시지**: "이미 사용 중인 이메일입니다." (탈퇴 사실 비노출 — 보안 정책)
  - 운영 CS에 "탈퇴 후 동일 email 재가입 문의"가 누적되면 (a) 또는 (b) 도입 슬라이스 trigger.

### CS 대응 스크립트 (초안)
운영 CS가 "탈퇴 후 동일 email 재가입이 안 됩니다" 문의 수신 시 표준 응답:
> "탈퇴하신 이메일은 보안 정책상 재사용이 제한됩니다. 다른 이메일(Gmail 별칭 `+suffix`, 보조 메일 등)로 신규 가입 부탁드립니다. 동일 이메일 복구 요청은 운영팀 검토 후 처리됩니다."

- 운영팀 수동 복구 절차: DBA가 `wallets`/`payments` 데이터 보존 확인 → `users.deleted_at = NULL` 복구 — 본 슬라이스 범위 외 (별도 admin 도메인 Epic).

### 구현 결과
- 회원가입 흐름의 `existsByEmail` + DB UNIQUE 제약이 자연 차단. 첫 체크는 `@SQLRestriction` 필터로 false 반환 → flush → DB unique 위반 → `DataIntegrityViolationException` catch.
- catch 블록의 race recheck를 `accountRepository.countByEmailIncludingDeleted`(native SQL, `@SQLRestriction` 우회) 호출로 보강 — soft-deleted row까지 보고 정확히 `AUTH_008`로 변환. 본 보강 전에는 catch 블록이 falls through해 raw 500을 던지던 잠재 버그가 있었음 (S1까지 미발현, S2 통합 테스트가 노출).
- 영속성 컨텍스트 정리: catch 블록 진입 직후 `EntityManager.clear()` — flush 실패한 Account 엔티티가 broken state로 세션에 남아 후속 query auto-flush 시 `AssertionFailure`를 일으키는 패턴 차단.
- 통합 테스트: 탈퇴 후 동일 email 재가입 시도 → `AUTH_008` 응답 확인 (`AccountWithdrawalCascadeIntegrationTest#same_email_resignup_after_withdrawal_returns_AUTH_008`).

### 후속 트리거 조건
- 운영 CS 분기 누적 (월 N건 이상)
- GDPR/PIPA "잊혀질 권리" 요구 시 동일 email 재사용을 사용자 권리로 명문화 필요
- 운영 PRD가 grace period (탈퇴 후 30일 내 복구) 도입 시 — partial unique index 자연 동반

## §4. ACCOUNT_DELETED audit event

### 결정
탈퇴 트랜잭션 commit 직후(`afterCommit`) `SecurityAuditEventType.ACCOUNT_DELETED` 발행.

### 근거
- **운영 추적성**: CS 대응 ("내가 언제 탈퇴했나요?"), 컴플라이언스 ("탈퇴 처리가 정상 발생했나?"), SIEM (대량 탈퇴 = 침해 사고 후 정리 작업 의심) 모두 audit 필요.
- **KAN-105 표준 채널 재사용**: 별도 logger / 별도 sink 추가 없이 `audit.security` 채널로 통합.
- **metadata**: `tokenRole` (Access Token claim 기준 권한 추적), `deletedLikes` (cascade 효과 가시성).

### 구현 결과
- `SecurityAuditEventType.ACCOUNT_DELETED` enum 추가.
- `audit-log-catalog.md`에 entry 추가 (UserMeService.deleteMe, SUCCESS, userId, tokenRole/deletedLikes).
- 발행 시점 = `afterCommit` (DB rollback 시 발행 차단).
- 동시 호출 race로 인한 중복 발행은 §5의 CAS UPDATE(`markAsDeleted`)가 차단 — 영향 row 1을 받은 호출자만 audit emit 경로 진입.
- metadata `tokenRole` — Access Token claim 기준 (DB 현재 role 아님). 토큰 발급 후 role 변경(USER → MERCHANT 승격 등) 시 발급 시점 role이 기록됨을 명시.

### 범위 외
- **관리자 강제 탈퇴 audit**: admin Epic 도래 시 별도 eventType (예: `ACCOUNT_FORCE_DELETED` + adminUserId metadata).
- **무결성 서명**: SIEM 자체 무결성 기능 의존. HMAC 서명은 별도 슬라이스.

## §5. 동시 탈퇴 race 가드 — CAS UPDATE

### 결정
`AccountRepository.markAsDeleted(userId, now)` 단일 SQL `UPDATE ... WHERE deleted_at IS NULL`로 atomic 처리. 영향받은 row 수가 0이면 이미 탈퇴됨 → AUTH_006.

### 배경
S1(KAN-143)의 `UserMeService.deleteMe`는 `findById` + `account.softDelete` 구조로, 두 요청이 거의 동시에 인증 필터를 통과하면:
- tx1, tx2 모두 ACTIVE 상태를 읽고 `softDelete` 호출
- 첫 commit 후 두 번째 호출이 `Account.softDelete`의 `IllegalStateException` 가드에 도달 → 500
- `ACCOUNT_DELETED` audit이 두 번 발행될 수 있음 (시점에 따라)

### 대안 비교

| 옵션 | 동작 | 비용 | 정확성 |
|---|---|---|---|
| (a) silent return / 멱등 | `IllegalStateException` catch → return | 최소 | audit 중복 가능 (catch 위치에 따라) |
| (b) `@Version` optimistic | 두 번째 호출 `OptimisticLockingFailureException` | Account에 `version` 컬럼 + schema 마이그레이션 | 정확하나 prod `ddl-auto=validate` 위험 |
| (c) PESSIMISTIC_WRITE | tx2가 tx1 commit 후 진입, `@SQLRestriction`으로 빈 결과 → AUTH_006 | 0 (기존 `findByIdWithLock` 재사용) | 이론상 정확하나 실측 불안정 — 아래 참고 |
| **(d) CAS UPDATE** | atomic `UPDATE ... WHERE deleted_at IS NULL` 영향 row 0이면 race | 0 (도메인 메서드 우회 없음 — 호출 후 별도 도메인 메서드 invoke X) | DB가 직접 보장 — race-window 0 |

### (d) CAS UPDATE 채택 사유
- **PESSIMISTIC_WRITE 실측 이슈**: Hibernate 7 + testcontainers MySQL 8.4 조합에서 `@SQLRestriction("deleted_at IS NULL")`이 `FOR UPDATE`와 결합할 때, tx2가 락 해제 후에도 stale snapshot으로 ACTIVE 엔티티를 반환하는 케이스가 통합 테스트에서 관측됨. status defensive 가드를 더해도 동일 증상 → 락 메커니즘 자체가 본 시나리오에서 무력화됨을 시사.
- **CAS UPDATE는 race-window 자체가 없음**: `UPDATE ... WHERE deleted_at IS NULL` 단일 SQL 명령. DB가 행 락 + 조건 평가 + 갱신을 원자적으로 수행. 두 동시 요청 중 한 쪽만 1행 UPDATE, 다른 쪽은 0행. 호출자는 영향 row 수로 race 결과를 알 수 있어 `ACCOUNT_DELETED` audit이 1건당 정확히 1번 발행되는 invariant를 DB가 직접 보장.
- **schema 변경 0**: 기존 컬럼(`status`, `deleted_at`) 그대로 사용. 마이그레이션 도구 도입 불필요.
- **UX 일관성**: 두 번째 요청이 401 AUTH_006으로 응답 — "당신은 더 이상 존재하지 않습니다, 재로그인" 의미. 500 보다 사용자 친화적.
- **운영 부하**: 단일 UPDATE 한 번이라 lock contention < PESSIMISTIC_WRITE.

### 구현 결과
- `AccountRepository.markAsDeleted(userId, now)` 신규 — `@Modifying @Query("UPDATE ... WHERE deleted_at IS NULL")`.
- `UserMeService.deleteMe` — `findById` + 도메인 메서드 호출 패턴에서 CAS UPDATE 호출 후 영향 row 검사로 변경.
- `Account.softDelete` 도메인 메서드는 비-동시성 컨텍스트(`AccountTest` 단위 테스트, 미래 API 확장)를 위해 유지. defense-in-depth.
- 통합 테스트: 동시 탈퇴 시뮬레이션(executor + CountDownLatch) → 한 쪽 204, 다른 쪽 4xx + `ACCOUNT_DELETED` audit 정확히 1건 검증.

## §6. 운영 체크리스트

탈퇴 흐름 변경/리뷰 시 검토:

- [ ] `UserMeService.deleteMe`가 `AccountRepository.markAsDeleted` CAS UPDATE 사용 — 변경 시 동시 race 회귀 가능
- [ ] `markAsDeleted` JPQL에 `updatedAt = :now` 포함 — JPA Auditing이 bulk update를 거치지 않으므로 누락 시 "최근 변경 계정" 조회/배치 회귀
- [ ] `UserLikeRepository.deleteByUserId`가 `markAsDeleted` 성공 직후 호출 — 순서 변경 시 영속성 컨텍스트 상태 점검
- [ ] `afterCommit` 안에서 audit emit + 토큰 invalidate 둘 다 발행 — 누락 시 운영 가시성 손실
- [ ] `audit-log-catalog.md`와 `SecurityAuditEventType` 동기 — 신규 eventType 추가 시 양쪽 업데이트
- [ ] `ACCOUNT_DELETED` metadata key가 `tokenRole`(`role` 아님) — DB 현재 role과 구분
- [ ] `countByEmailIncludingDeleted` native query의 테이블명 `users`가 `Account.@Table(name=...)`와 동기 — 테이블명 변경 시 함께 갱신
- [ ] `wallets` 테이블 row가 탈퇴 후에도 살아있는지 통합 테스트 회귀 가드
- [ ] 동일 email 재가입이 `AUTH_008`로 차단되는지 회귀 가드 (정책 변경 시 본 ADR 갱신)

## §7. 후속 / 범위 외

- **partial unique index 도입** (§3 (a)/(b)) — 운영 CS 요구 발생 시 별도 슬라이스
- **grace period** (탈퇴 후 N일 내 복구) — UX 결정 후 별도 슬라이스
- **탈퇴 사유 수집** (드롭다운/자유 텍스트) — 운영 PRD 추가 후 별도 슬라이스
- **관리자 강제 탈퇴** + `ACCOUNT_FORCE_DELETED` audit — admin 도메인 Epic
- **`logoutTokenStore.invalidate` 재시도 / DLQ** — Redis 장애 시 토큰 무효화 누락 보강 (별도 KAN)

## §8. 참조

- 본 슬라이스 PRD = `tmp/jira-drafts/_DONE_S1_KAN-142-epic-c-account-withdrawal.md` § 3. Story S2
- S1 슬라이스 (`KAN-143`) — soft delete + 토큰 cascade 무효화 도입
- KAN-105 audit infrastructure — `docs/operations/audit-log-catalog.md`
- KAN-124 — `Place.likeCount` 캐시 컬럼 (UserLike hard delete 통계 무영향 근거)
- STORY-09 — `AccountRepository.findByIdWithLock` 도입 (상인 승인 흐름)
- sa-docs/11 운영 보안 정책 § 회원 lifecycle
