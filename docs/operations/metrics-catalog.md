# Metrics Catalog (chunbae-tour)

> 인증/Rate Limit/회원가입 도메인의 운영 메트릭 카탈로그.
> 노출 endpoint = `/actuator/prometheus` (Spring Boot Actuator + Micrometer Prometheus). KAN-104.

## 노출 정책

| Endpoint | 권한 | 용도 |
|---|---|---|
| `/actuator/health` | permitAll | LB health check |
| `/actuator/info` | permitAll | 빌드/배포 정보 |
| `/actuator/prometheus` | permitAll (prod는 후속 IP allowlist 필수) | Prometheus scrape |
| `/actuator/**` (그 외) | denyAll | env/beans/mappings 등 정보 노출 차단 |

**운영 배포 전 필수 정비**: `/actuator/prometheus` IP allowlist. 옵션:
1. Spring Security `hasIpAddress` 매핑 (운영 모니터링 인스턴스 IP만 허용)
2. `management.server.port` 분리 (예: 8081) → LB에서 외부 노출 안 함
3. ECS Task에서 별도 sidecar로 분리

본 PR(KAN-104)에서는 hook만 마련. 실제 정책은 인프라 결정 의존.

## 공통 태그

모든 메트릭에 자동 부착:

| 태그 | 값 |
|---|---|
| `application` | `chunbae-tour` |
| `env` | `${SPRING_PROFILES_ACTIVE}` (local / prod) |

## 메트릭 카탈로그

### JWT 검증 (JwtAuthenticationFilter)

#### `auth.jwt.verify.duration` (Timer)

JWT 검증 + 블랙리스트 조회까지 포함한 처리 시간.

| 태그 | 값 |
|---|---|
| `outcome` | `success` / `expired` / `tampered` / `blacklisted` / `redis_failure` |

Prometheus에서는 `auth_jwt_verify_duration_seconds_count` (호출 횟수), `_sum` (누적 시간), `_max` (최대 시간), `_bucket` (히스토그램 quantile) 형태.

**권장 쿼리**:
- p95 latency: `histogram_quantile(0.95, sum by (le) (rate(auth_jwt_verify_duration_seconds_bucket{outcome="success"}[5m])))`
- success 처리량: `rate(auth_jwt_verify_duration_seconds_count{outcome="success"}[1m])`

**권장 알람**: p95 latency > 50ms (5분 평균) — JWT 검증이 운영 트래픽에서 병목.

#### `auth.jwt.failure.total` (Counter)

JWT 검증 실패 빈도.

| 태그 | 값 |
|---|---|
| `code` | `AUTH_002` (expired) / `AUTH_003` (tampered) / `AUTH_013` (blacklisted) |

**권장 쿼리**:
- 변조 공격 감지: `rate(auth_jwt_failure_total{code="AUTH_003"}[5m]) > 0.1` (5분 평균 분당 6회 이상)
- 블랙리스트 재사용: `rate(auth_jwt_failure_total{code="AUTH_013"}[5m])` — 탈취 의심 토큰 활용 시도

**권장 알람**: `AUTH_003` rate가 평소 대비 10배 → SIEM 알람.

### Rate Limit (RateLimitFilter / RedisRateLimiter)

#### `ratelimit.decision.total` (Counter)

Rate limit 판정 빈도.

| 태그 | 값 |
|---|---|
| `endpoint` | `signup` / `user-login` / `merchant-login` / `admin-login` (yml `ratelimit.endpoints[].id`) |
| `decision` | `allowed` / `denied` |

**권장 쿼리**:
- endpoint별 거부율: `rate(ratelimit_decision_total{decision="denied"}[5m]) / rate(ratelimit_decision_total[5m])`
- 가장 공격받는 endpoint: `topk(3, sum by (endpoint) (rate(ratelimit_decision_total{decision="denied"}[5m])))`

**권장 알람**: 같은 endpoint denied rate > 분당 50회 → 분산 brute force 의심.

#### `ratelimit.redis.failure.total` (Counter)

Redis 장애로 fail-closed 처리한 빈도.

태그 없음 (Redis 장애는 endpoint별로 다르지 않음).

**권장 쿼리**: `rate(ratelimit_redis_failure_total[5m])`

**권장 알람**: `> 0` (즉시) — Redis 장애 = 모든 회원가입/로그인 일시 차단. 운영 즉시 대응 필요.

### 로그인 / 회원가입 (LoginService / SignupService)

#### `auth.login.attempt.total` (Counter)

로그인 시도 빈도.

| 태그 | 값 |
|---|---|
| `outcome` | `success` / `invalid_password` / `role_mismatch` / `suspended` |

`invalid_password`는 이메일 미존재 + 비밀번호 불일치를 모두 포함 (외부 응답이 AUTH_001로 동일).

**권장 쿼리**:
- 성공률: `rate(auth_login_attempt_total{outcome="success"}[5m]) / rate(auth_login_attempt_total[5m])`
- brute force 감지: `rate(auth_login_attempt_total{outcome="invalid_password"}[5m]) > 1` (분당 60회 이상)

**권장 알람**:
- `invalid_password` rate 평소 대비 10배 → brute force 의심
- `role_mismatch` 갑작스러운 증가 → 권한 우회 시도 의심

#### `auth.signup.attempt.total` (Counter)

회원가입 시도 빈도.

| 태그 | 값 |
|---|---|
| `outcome` | `success` / `email_dup` / `nickname_dup` / `db_failure` |

**권장 쿼리**:
- 가입 성공률: `rate(auth_signup_attempt_total{outcome="success"}[1h])`
- 중복 충돌율: `rate(auth_signup_attempt_total{outcome=~"email_dup\|nickname_dup"}[1h])`

**권장 알람**: `db_failure` rate > 0 → DB 무결성 위반 또는 race 폭증 의심.

## 미수집 영역 (후속)

본 슬라이스는 인증 도메인 한정. 다음 영역은 별도 메트릭 슬라이스로 분리:

- 결제 흐름 (PaymentGatewayClient / WebhookController / CallbackService) — KAN-70 도메인
- Place / Search / Chat / Yeopjeon 도메인
- 분산 트레이싱 (Sleuth / OpenTelemetry) — 별도 Epic
- 비즈니스 KPI (DAU, MAU, 매출) — 별도 도메인

## Grafana 대시보드 (참고)

본 슬라이스 범위 외. 권장 panel 구성:

1. **Auth Latency**: JWT verify p50/p95/p99 (success outcome)
2. **Auth Failures**: AUTH_002/003/013 rate per minute
3. **Rate Limit per Endpoint**: allowed vs denied stacked bar
4. **Redis Health (Rate Limit)**: redis failure rate
5. **Login Success Rate**: success / total %
6. **Signup Funnel**: success / email_dup / nickname_dup / db_failure

대시보드 JSON은 운영 인프라 작업 (Grafana 클러스터 의존).

## 참조

- KAN-104 PRD (`docs/prd/KAN-104-epic-b-s3-auth-filter-monitoring.md`)
- ADR 0002 Phase 2 ECS 전환 시 CloudWatch 또는 Managed Prometheus 검토 (`docs/adr/0002-secret-injection-standard.md`)
- sa-docs/11 운영 보안 정책 § 모니터링
