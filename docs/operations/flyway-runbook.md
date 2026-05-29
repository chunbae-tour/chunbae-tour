# Flyway 운영 Runbook (KAN-178, Admin Epic KAN-177 S00)

> chunbae-tour 백엔드의 DB 스키마 변경은 **모두 Flyway migration**으로 관리한다.
> 본 문서는 운영 prod 적용 절차 + 새 마이그레이션 작성 가이드 + 트러블슈팅 + 팀 규칙을 담는다.

---

## 1. 운영 prod baselineOnMigrate 첫 적용 절차

### 1.1 사전 확인 (배포 전)

운영 prod MySQL에 접속해 아래 두 조건 모두 확인:

```sql
-- (1) 기존 schema가 정상 존재
SHOW TABLES;
-- → users / wallets / payment_orders 등 운영 테이블 모두 보여야 함

-- (2) flyway_schema_history 부재
SHOW TABLES LIKE 'flyway_schema_history';
-- → Empty set 이어야 함
```

> 만약 `flyway_schema_history` 테이블이 이미 존재한다면 절대 본 배포 진행 금지. 즉시 운영 책임자와 상의 (다른 인스턴스에서 Flyway가 먼저 동작했을 가능성).

### 1.2 배포 + 자동 적용

코드 배포 → 운영 prod 앱 시작 → Spring Boot 부팅 시점에 Flyway 자동 실행.

Flyway 동작 흐름:

1. `flyway_schema_history` 부재 감지
2. `baseline-on-migrate=true` + `baseline-version=1` 정책에 따라 V1을 적용된 것으로 간주
3. `flyway_schema_history` 테이블 신설 + V1 row 1건 기록 (`type=BASELINE` + `success=true`)
4. 이후 V2~가 있으면 자동 적용 (본 슬라이스는 V1만 존재)

→ **prod DB schema 자체는 변경 0**. schema_history 테이블 신설만 발생.

### 1.3 적용 직후 검증

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

기대 결과:

| installed_rank | version | description | type | success |
|---|---|---|---|---|
| 1 | 1 | << Flyway Baseline >> | BASELINE | 1 (true) |

→ 위와 다르면 즉시 롤백 (아래 §3 트러블슈팅 참조).

### 1.4 후속 슬라이스의 V2~ 적용

본 S00 머지 후 S01 (AdminActionLog) 등 후속 슬라이스가 `V2__create_admin_action_logs.sql` 등을 추가하면 다음 배포 시 Flyway가 자동으로 V2를 prod에 적용. 운영자는 배포 후 schema_history에 V2 row가 success=true로 기록됐는지만 확인.

---

## 2. 새 마이그레이션 작성 가이드

### 2.1 파일명 규칙

```
src/main/resources/db/migration/V{버전}__{snake_case_설명}.sql
```

- **버전 번호 = monotonic integer**. 마지막 V버전 + 1.
- 설명은 snake_case 동사 위주 (예: `V2__create_admin_action_logs.sql`, `V3__alter_users_add_suspended_reason.sql`).
- 한 파일 한 의도 — 두 테이블을 같은 V에 묶을 때도 한 주제로 통일.

### 2.2 PR 본문 표기

PR 본문에 `Flyway: V{N}__xxx.sql` 한 줄 명시. 리뷰어가 SQL 변경을 빠르게 확인 가능.

```
## Flyway
- V2__create_admin_action_logs.sql (신규)
```

### 2.3 작성 원칙

- 한 번 적용된 V파일은 **수정 금지**. 정정은 `V{N+1}` 추가로.
- `IF NOT EXISTS` / `IF EXISTS` 가드는 baseline 외에는 권장 X (의도된 schema 변경이 silent skip되면 추적 어려움).
- 외래키(FK) 추가는 별도 V로 분리 검토 — 기존 데이터에 위반 row가 있으면 마이그레이션 실패.
- 큰 ALTER (다중 컬럼, 인덱스 다수)는 운영 영향 검토 후 진행.

### 2.4 V버전 충돌 시 (협업)

두 PR이 동시에 같은 V버전 사용하면 Flyway가 거부. 해결:

1. 머지 순서가 빠른 쪽이 먼저 머지
2. 다른 PR은 V버전을 + 1 하여 rebase + 본 PR description의 V표기 갱신
3. INDEX.md "Flyway 버전 사전 매핑" 표를 사용해 사전 조율 (있다면)

---

## 3. 트러블슈팅

### 3.1 마이그레이션 실패 (success=false row 존재)

```sql
SELECT version, description, success, execution_time
FROM flyway_schema_history
WHERE success = 0;
```

→ 실패 row 발견 시 옵션:

**(A) 수동 수정 후 repair**

1. 실패한 SQL을 운영 DBA가 수동으로 정정 적용
2. `DELETE FROM flyway_schema_history WHERE success = 0;` 또는 `flyway repair` 실행
3. 앱 재배포 → 다음 마이그레이션 자동 적용

**(B) 부분 적용 롤백**

실패 마이그레이션이 부분 적용된 경우 (예: 테이블 생성됐는데 인덱스 실패):

1. 운영 DBA가 부분 적용 상태를 수동 정리 (테이블 DROP 등)
2. `DELETE FROM flyway_schema_history WHERE success = 0;`
3. V파일 정정 (필요 시 V{N+1}로 분리)
4. 재배포

### 3.2 checksum mismatch

V파일을 머지 후 수정한 경우 (금지 위반). 해결:

1. 정정 의도라면 **V파일 원래대로 복구** + 별도 `V{N+1}` 신규 작성
2. 일시적 보존 (의도된 변경) 라면 `flyway repair`로 checksum 갱신 — 운영 책임자 승인 필수

### 3.3 운영 prod 첫 배포 실패 (V1 없는데 schema_history 생성됨)

`baseline-on-migrate=true`인데 첫 배포에서 schema_history만 생성되고 V1 row 없는 경우:

1. `flyway_schema_history` 테이블 DROP
2. 운영 책임자가 prod DB 상태 확인 (schema 변경 0인지)
3. 재배포

### 3.4 testcontainers Flyway 충돌 (CI 실패)

`./gradlew test` 단계에서 `FlywayBaselineIntegrationTest` 실패:

- **flyway_schema_history 부재**: `application.yml`의 `spring.flyway.enabled` 확인
- **entity validate 실패**: V1__baseline.sql이 entity와 일치 안 함 → V1 SQL 보강
- **V버전 충돌**: 본 슬라이스에는 V1만 존재해야 함. V2가 있으면 후속 슬라이스 작업

---

## 4. 팀 규칙 (강제)

| 규칙 | 사유 |
|---|---|
| **수동 SQL 직접 prod 실행 금지** | Flyway가 추적할 수 없으면 환경 drift 발생. CI/CD 신뢰 손실 |
| **한 번 적용된 V파일 수정 금지** | checksum 불일치 → 부팅 차단. 정정은 `V{N+1}` 추가로 |
| **`ddl-auto: create` / `update` 사용 금지** | 전 환경 `validate` 고정. entity 변경 시 V파일 반드시 동반 |
| **`R__` repeatable migrations 사용 범위** | view / function / index만. table 변경은 `V` 사용 |
| **PR에 V파일 누락 시 CI 차단** | `testcontainers` + `ddl-auto: validate` 자동 검증 (entity ↔ V1 일치) |
| **운영 prod baseline 첫 적용 모니터링** | 운영 책임자(단일 실패점 해소)가 첫 배포 시 schema_history V1 row 확인 책임 |

---

## 5. 첨부 — Flyway 핵심 설정 (참고)

`application.yml` (전역, 본 슬라이스 도입):

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## 6. 관련 문서

- Admin Epic 마스터: `tmp/jira-drafts/kan-177-admin-domain/INDEX.md`
- S00 PRD: `tmp/jira-drafts/kan-177-admin-domain/S00-flyway-setup.md`
- KAN-88 secrets-catalog (운영 인프라 결정 ADR 패턴): `docs/operations/secrets-catalog.md`
- KAN-105 audit log 운영 가이드: `docs/operations/audit-log-catalog.md`
