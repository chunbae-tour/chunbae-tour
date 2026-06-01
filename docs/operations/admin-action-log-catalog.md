# AdminActionLog 운영 액션 카탈로그 (KAN-179, Admin Epic KAN-177 S01)

> 운영 액션 추적의 표준 enum 정의. 새 액션 추가 시 본 문서를 함께 갱신해야 한다.

---

## 1. 목적

운영자가 admin 컨트롤러에서 수행하는 액션(정지/승인/거절/콘텐츠 변경 등)을 누가/무엇을/언제/왜 했는지 영구 기록한다. 분쟁 대응, CS 응대, 권한 남용 추적, GDPR/PIPA 감사 요구를 동시 해소.

저장 위치: `admin_action_logs` 테이블 (V2). append-only.

---

## 2. actionType 카탈로그

본 슬라이스(S01)는 인프라만 제공하므로 enum 초기 항목은 2개. 후속 슬라이스가 자신의 액션을 추가하며 본 카탈로그도 함께 갱신한다.

| actionType | 도입 슬라이스 | 의미 | 대표 targetType |
|---|---|---|---|
| `USER_SUSPEND` | S02 | 사용자 정지 (사유 + 기간) | `USER` |
| `USER_UNSUSPEND` | S02 | 사용자 정지 해제 | `USER` |
| `CERTIFICATION_APPROVE` | S05 | 인증 승인 (Shop.markCertified() cascade) | `SHOP_CERTIFICATION` |
| `CERTIFICATION_REJECT` | S05 | 인증 거절 (사유 기록) | `SHOP_CERTIFICATION` |
| `CERTIFICATION_CANCEL` | S05 | 인증 취소 (Shop.unmarkCertified() 회수) | `SHOP_CERTIFICATION` |

후속 슬라이스 예상 추가 항목 (PRD INDEX.md 결정 2 참조):

| actionType | 슬라이스 | targetType |
|---|---|---|
| `SHOP_UPDATE` | S04 | `SHOP` |
| `PLACE_CREATE` / `UPDATE` / `DELETE` | S07 | `PLACE` |
| `FESTIVAL_CREATE` / `UPDATE` / `DELETE` | S08 | `FESTIVAL` |
| `BANNER_CREATE` / `UPDATE` / `DELETE` | S09 | `BANNER` |
| `FAQ_CREATE` / `UPDATE` / `DELETE` | S11 | `FAQ` |
| `SUPPORT_CLOSE` | S12 | `SUPPORT_ROOM` |

> 위 표는 예상치이며 실제 enum 항목 추가는 각 슬라이스 PR이 책임.

---

## 3. targetType 카탈로그

본 슬라이스에서 13개 전부 정의 (후속 슬라이스가 사용할 도메인 모두 포함). 새 도메인 admin이 도입되면 항목 추가 + 본 표 갱신.

| targetType | 대상 도메인 | 비고 |
|---|---|---|
| `USER` | 일반 사용자 | suspended_reason 등 user 컬럼 변경 |
| `MERCHANT` | 상인 계정 | role=MERCHANT 회수 등 |
| `SHOP` | 가게 | status 변경, description 수정 등 |
| `SHOP_CERTIFICATION` | 인증 신청 | 승인/거절/취소 |
| `MERCHANT_APPLICATION` | 상인 신청 | 승인/거절 |
| `AD_APPLICATION` | 광고 신청 | 승인/거절 (KAN-162) |
| `REPORT` | 신고 | resolve/dismiss (KAN-91~93) |
| `REFUND` | 환불 | 승인/거절 (AdminRefund) |
| `PLACE` | 관광지/전통시장 | CRUD |
| `FESTIVAL` | 축제 | CRUD (baseline 명칭 일관 — `EVENT` 사용 X) |
| `BANNER` | 추천 배너 | CRUD |
| `FAQ` | FAQ | CRUD |
| `SUPPORT_ROOM` | 고객센터 상담방 | close |

---

## 3.5. 필드 보강 전략

AdminActionLog 1건의 필드는 출처가 다르다. S01(인프라)은 어노테이션 + path variable만 채우고, 나머지는 후속 슬라이스가 보강한다.

| 필드 | 출처 | S01 채움 여부 |
|---|---|---|
| `actionType` / `targetType` | `@LogAdminAction` 어노테이션 속성 | ✅ |
| `targetId` | URI path variable (`targetIdVar` 지정 시 결정적, 미지정 시 Long 후보 1개일 때만) | ✅ |
| `reason` | request body | ❌ 항상 null — 후속 슬라이스 보강 |
| `beforeStatus` / `afterStatus` | 액션 전후 도메인 상태 | ❌ 항상 null — 후속 슬라이스 보강 |

> `reason` / `beforeStatus` / `afterStatus` 보강은 후속 슬라이스가 request body/result에서 추출하는 **별도 전략**이다. `@LogAdminAction`에 `reason()` 등의 속성을 추가하지 않는다(미사용 YAGNI).
>
> **status를 String으로 두는 이유** — 13개 targetType마다 상태 도메인(UserStatus / ShopStatus / CertificationStatus ...)이 상이하다. 공통 enum화는 불가능하므로 의도적으로 polymorphic String을 채택한다.

---

## 4. 새 actionType / targetType 추가 절차

1. `AdminActionType` 또는 `AdminTargetType` enum에 항목 추가
2. 본 카탈로그(`docs/operations/admin-action-log-catalog.md`)에 행 추가
3. 대상 컨트롤러 메서드에 `@LogAdminAction(actionType=..., targetType=...)` 부착
4. 통합 테스트 — 해당 endpoint 호출 후 `admin_action_logs`에 row 1건 기록 확인
5. PR 본문에 enum 추가 항목 명시 (리뷰어 추적 편의)

> ⚠️ enum 이름 변경 금지 — DB는 enum 이름을 그대로 VARCHAR(64)에 저장한다. 변경은 별도 Flyway 마이그레이션 + 기존 row 호환성 검토 필수.

---

## 5. AdminActionLog ↔ SecurityAuditEvent 분리 (KAN-105)

| 항목 | AdminActionLog (KAN-179) | SecurityAuditEvent (KAN-105) |
|---|---|---|
| 책임 | 운영 액션 추적 (CS/감사/추후 조회 UI) | 인증·보안 사건 표준 (이상 로그인/CSRF 차단 등) |
| 저장소 | `admin_action_logs` 테이블 (DB) | 별도 logger 채널 (file appender + JSON stdout) |
| 조회 방법 | 후속 슬라이스의 admin 조회 UI | 외부 log aggregator (ELK/CloudWatch Logs) |
| 발행 트리거 | admin 컨트롤러 메서드 (수동/auto wire) | 인증 필터 + 보안 이벤트 자체 |
| 데이터 보존 | 영구 (DB row, append-only) | logger retention 정책 (운영 보관 기간 별도) |

→ 두 도메인 모두 운영 추적이 목적이지만 청중(audit DB UI vs 보안 log pipeline) + 발행 트리거가 다름. 본 슬라이스가 KAN-105를 흡수하거나 대체 X.

---

## 6. Out of Scope (후속 슬라이스 검토 항목)

- **마스킹** — 비밀번호/카드번호/PII 마스킹은 본격 버전 후속. 마스킹 정책 ADR 필요
- **비동기** — `@Async` 또는 Spring Event + Redis Streams 분리 (응답 latency +5~10ms 영향 발생 시)
- **샘플링** — 대량 트래픽 시 액션 종류별 차등 샘플링 정책
- **request body 전체 저장** — 현재 actionType/targetType/before/after만. JSON 직렬화로 payload 전체 저장은 후속
- **조회 endpoint** — `GET /admin/action-logs` 별도 슬라이스 (cursor 페이징 + 필터)
- **IP/User-Agent 수집** — `RequestContextHolder` + `IpMaskUtil` (KAN-105) 재사용 검토

---

## 7. 관련 문서

- Admin Epic 마스터: `tmp/jira-drafts/kan-177-admin-domain/INDEX.md`
- S01 PRD: `tmp/jira-drafts/kan-177-admin-domain/S01-admin-action-log.md`
- KAN-105 보안 감사 로그 운영 가이드: `docs/operations/audit-log-catalog.md`
- KAN-178 Flyway runbook: `docs/operations/flyway-runbook.md`
