# Security Audit Log Catalog (chunbae-tour)

> 인증 도메인의 보안 감사 로그 카탈로그 (KAN-105 Epic KAN-64 S4).
> 운영 사고 발생 시 추적/포렌식 자료. logger name = `audit.security`.

## 출력 위치

| 환경 | Sink | 비고 |
|---|---|---|
| local / test | `logs/audit-security.log` (RollingFileAppender + Logstash JSON) | 100MB 회전 + 365일 보존 |
| prod | 위 파일 appender + **stdout JSON** (`AUDIT_JSON_STDOUT`) | ECS Task / CloudWatch Logs 수집기가 stdout 캡처 |

JSON encoder = `net.logstash.logback.encoder.LogstashEncoder`. 외부 log aggregator(ELK/CloudWatch Logs/Datadog)가 별도 인덱스로 수집 가능.

`additivity="false"` — root console로 전파 차단해 일반 application 로그와 분리.

## 이벤트 카탈로그

| eventType | 발생 지점 | outcome | actorId | metadata 키 |
|---|---|---|---|---|
| `LOGIN_SUCCESS` | LoginService | SUCCESS | account.id | role |
| `LOGIN_FAILURE` | LoginService | FAILURE | account.id 또는 null | requiredRole, reasonDetail (email_not_found/wrong_password/actualRole) |
| `SIGNUP_SUCCESS` | SignupService | SUCCESS | saved.id | role |
| `SIGNUP_FAILURE` | SignupService | FAILURE | null | reasonDetail (email_dup/nickname_dup/email_dup_race/nickname_dup_race/data_integrity_violation) |
| `LOGOUT` | LogoutService | SUCCESS | claims.userId | role |
| `TOKEN_EXPIRED` | JwtAuthenticationFilter | FAILURE | null | - |
| `TOKEN_TAMPERED` | JwtAuthenticationFilter | FAILURE | null | - |
| `TOKEN_BLACKLISTED` | JwtAuthenticationFilter | FAILURE | claims.userId | - |
| `REFRESH_ROTATED` | ReissueService | SUCCESS | account.id | role |
| `REFRESH_REJECTED` | ReissueService | FAILURE | userId 또는 null | reasonDetail (jwt_expired/jwt_invalid/account_not_found/account_suspended/cas_failure) |
| `RATE_LIMIT_DENIED` | RateLimitFilter | FAILURE | null | endpoint |

## 표준 필드 (모든 이벤트 공통)

JSON 최상위 필드로 직렬화. MDC prefix `audit.`로 펼침 — Kibana/CloudWatch query 시 단순 필드 접근 가능.

| 필드 | 타입 | 의미 |
|---|---|---|
| `audit.timestamp` | ISO-8601 Instant | 이벤트 발생 시각 (UTC) |
| `audit.eventType` | string | 카탈로그 enum 값 |
| `audit.outcome` | `SUCCESS` / `FAILURE` | SIEM 룰 1차 분기 |
| `audit.actorId` | Long (string화) | 인증된 사용자 ID. 미인증 흐름은 미포함 |
| `audit.targetUserId` | Long | 본인 외 대상. 현재는 미사용 (admin 도메인 도래 시) |
| `audit.ipMasked` | string | 마스킹된 클라이언트 IP (`192.168.0.***`) |
| `audit.userAgent` | string | User-Agent 헤더 (200자 잘림) |
| `audit.reason` | string | 실패 사유 코드 (AUTH_001 등) |
| `audit.meta.<key>` | string | 추가 컨텍스트 (endpoint, role 등) |

## 민감 정보 금지 목록

본 자료형(`SecurityAuditEvent` record)에 다음 필드는 **존재하지 않음** → 컴파일 단계에서 노출 차단:

- 비밀번호 (raw / hashed 모두)
- JWT 본문 (Access / Refresh)
- Refresh Token 본문
- Cookie 값 / Authorization 헤더 원본
- 이메일 주소 (actorId 매핑으로 대체)

`metadata` 맵에도 위 항목 키/값 절대 넣지 말 것. 회귀 가드 = `SecurityAuditLoggerTest.보안_회귀_민감_정보_미포함()`.

## 권장 SIEM 알람 룰

운영 환경에서 ELK/Splunk/CloudWatch Insights 기준 예시:

| 알람 | 조건 |
|---|---|
| **Brute force 의심** | `audit.eventType=LOGIN_FAILURE` + `audit.meta.reasonDetail=wrong_password` rate > 분당 30회 |
| **토큰 변조 공격** | `audit.eventType=TOKEN_TAMPERED` rate > 0 (즉시 알람) |
| **탈취 토큰 재사용** | `audit.eventType=TOKEN_BLACKLISTED` rate > 0 (탈취 의심 actorId 추출) |
| **권한 우회 시도** | `audit.eventType=LOGIN_FAILURE` + `audit.meta.requiredRole != audit.meta.actualRole` (admin login에 user 계정 등) |
| **Refresh 탈취 의심** | `audit.eventType=REFRESH_REJECTED` + `audit.meta.reasonDetail=cas_failure` rate > 분당 5회 |
| **분산 brute force** | `audit.eventType=RATE_LIMIT_DENIED` + 같은 endpoint count > 분당 100 |

## 보존 정책

- 로컬 파일: 365일 (logback `maxHistory`) + 100MB 회전 + 10GB 전체 cap
- prod stdout JSON: 외부 수집기 정책에 따름 (CloudWatch Logs retention 등)

운영 정책 변경 시 `logback-spring.xml` `RollingFileAppender`의 `maxHistory` / `totalSizeCap` 갱신.

## 신규 EventType 추가 절차

1. 본 카탈로그에 행 추가 (eventType / 발생 지점 / outcome / actorId / metadata)
2. `SecurityAuditEventType` enum에 항목 추가 (Javadoc 동기)
3. 해당 도메인 hook 추가 — `auditLogger.emitSuccess/emitFailure` 호출
4. 단위 테스트 — emit 호출이 정확한 eventType + metadata로 발생하는지 검증
5. SIEM 알람 룰 검토 — 새 eventType이 알람 트리거 대상이면 위 표에도 추가

## S3 메트릭 (KAN-104)과 상호 보완

- **S3 메트릭** = aggregated count (`rate(auth_login_attempt_total{outcome="invalid_password"}[5m])`) — 트렌드/spike 감지
- **S4 감사 로그** = individual events — 포렌식, actorId/IP/User-Agent 패턴 분석
- 두 슬라이스가 별개 sink로 분리 → 모니터링/포렌식 책임 분리

## 미수집 영역 (후속)

- 관리자 권한 변경 / 정지 처리 — admin Epic 도래 시
- 비밀번호 변경 / 재설정 — 별도 PRD
- 회원 탈퇴 — 별도 PRD
- 결제 도메인 감사 (PortOne webhook 등) — KAN-70 도메인 작업 시 동일 `SecurityAuditLogger` 재사용
- 감사 로그 무결성 (HMAC 서명) — SIEM 자체 무결성 기능 의존 또는 별도 Story

## 참조

- KAN-105 PRD (`docs/prd/KAN-105-epic-b-s4-security-audit-log.md`)
- S3 메트릭 카탈로그 (`docs/operations/metrics-catalog.md`)
- ADR 0002 Phase 2 ECS 전환 시 CloudWatch Logs 연동 자연스러움
- sa-docs/11 운영 보안 정책 § 감사 로그
