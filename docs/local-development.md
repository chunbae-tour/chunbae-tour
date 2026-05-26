# 로컬 개발 환경 사용 가이드

이 문서는 팀원이 Windows, macOS, Linux에서 Docker 기반 로컬 개발 환경을 같은 방식으로 실행할 수 있도록 정리한 가이드입니다.

## 이 설정이 해주는 일

- MySQL 8.4 컨테이너를 실행합니다.
- Redis 7 컨테이너를 실행합니다.
- MySQL/Redis 포트와 DB 계정 정보를 `.env`에서 관리합니다.
- 팀원마다 로컬 포트가 다를 때 `.env`만 수정해서 충돌을 피할 수 있습니다.
- Spring Boot `local` 프로필이 `.env` 값을 읽어 Docker Compose 설정과 같은 DB/Redis에 접속합니다.
- `scripts/dev-up.*`, `scripts/dev-down.*` 스크립트로 OS별 실행 명령 차이를 줄입니다.

## 처음 한 번만 준비할 것

1. Java 21을 설치합니다.
2. Docker Desktop을 설치하고 실행합니다.
3. 프로젝트 루트에서 `.env` 파일을 만듭니다.

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

## 기본 `.env` 값

```dotenv
DB_HOST=localhost
DB_PORT=3307
DB_NAME=chunbae_tour
DB_USERNAME=chunbae
DB_PASSWORD=1234

REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=

JWT_SECRET=local-dev-only-secret-replace-me-min-32-bytes-xxxx
JWT_ACCESS_TOKEN_TTL=PT30M
JWT_REFRESH_TOKEN_TTL=P7D

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

보통은 이 값 그대로 사용하면 됩니다.

## JWT 시크릿

- `JWT_SECRET`은 JWT(HS256) 서명에 사용되는 비밀 키입니다.
- 로컬 개발에서는 `.env.example`에 포함된 dummy 값을 그대로 사용해도 됩니다. **운영 환경에서는 절대 사용하지 마세요.**
- 최소 32 바이트(영문/숫자 32자) 이상이어야 합니다. 부족하면 애플리케이션이 부팅 시 실패합니다.
- 운영 환경은 `application-prod.yml`이 `${JWT_SECRET}` 환경변수를 필수로 요구합니다. 환경변수 주입 방식(배포 인프라 시크릿 저장소 등)은 인프라 PRD에서 정합니다.
- `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`은 ISO-8601 Duration 형식입니다. 기본값은 각각 30분(`PT30M`), 7일(`P7D`)이며, `.env`에서 비워두면 `application.yml` default 값이 사용됩니다.

## CORS 정책

- `CORS_ALLOWED_ORIGINS`는 백엔드가 허용할 프론트엔드 origin(스킴+호스트+포트)을 콤마로 구분해서 나열합니다.
- 로컬 개발 기본값은 `http://localhost:3000`(Next.js)과 `http://localhost:5173`(Vite)입니다.
- **와일드카드(`*`) 금지**: Refresh Token이 HttpOnly Cookie로 전달되며 `allowCredentials=true`로 설정되어 있어, 브라우저가 와일드카드 origin을 거부합니다. 반드시 명시적 origin만 사용하세요.
- 운영 환경은 `application-prod.yml`이 `${CORS_ALLOWED_ORIGINS}`를 필수로 요구합니다 (default 없음).
- 다른 포트의 프론트엔드 서버를 띄우면 `.env`의 `CORS_ALLOWED_ORIGINS`에 해당 origin을 추가하세요.

## Refresh Token Cookie

- 로그인/재발급 응답은 `Set-Cookie: refreshToken=...; HttpOnly; SameSite=Lax; Path=/api/v1/auth` 헤더를 포함합니다.
- 로컬은 `Secure` 플래그가 빠집니다(HTTP). 운영은 `application-prod.yml`이 `Secure=true`로 강제합니다(HTTPS 전용).
- 클라이언트(프론트엔드)는 Cookie 값을 직접 읽거나 저장하지 않습니다.
- 단, 백엔드와 프론트엔드 origin이 다르면 요청에 자격증명 전송 옵션을 반드시 켜야 브라우저가 Cookie를 함께 보냅니다.
  - `fetch`: `fetch(url, { credentials: "include" })`
  - Axios: `axios.create({ withCredentials: true })` 또는 요청별 `{ withCredentials: true }`
- 재발급은 `POST /api/v1/auth/reissue` 호출 (위 credentials 옵션으로 Cookie 전송 + 새 Access Token 응답).

## Rate Limit

회원가입/로그인 endpoint에 IP 기반 rate limit이 적용되어 있습니다 (sa-docs/11 운영 보안 정책 §Rate Limit).

### 기본 정책

| Endpoint | 제한 | window |
|---|---|---|
| `POST /api/v1/users/auth/signup` | 3회 | 10분 |
| `POST /api/v1/users/auth/login` | 5회 | 1분 |
| `POST /api/v1/merchants/auth/login` | 5회 | 1분 |
| `POST /api/v1/admin/auth/login` | 5회 | 1분 |

한도 초과 시 응답:

- HTTP `429 Too Many Requests`
- Body: `{ "code": "AUTH_014", "message": "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요." }`
- 응답 헤더: `Retry-After`(초), `X-RateLimit-Limit`, `X-RateLimit-Remaining`

### 응답 헤더 (허용된 요청도 포함)

- `X-RateLimit-Limit`: 정책 한도
- `X-RateLimit-Remaining`: 본 요청 처리 후 남은 허용 횟수

클라이언트가 한도에 가까워졌을 때 미리 적응적 호출이 가능합니다.

### 로컬 개발에서 비활성화

같은 endpoint를 빠르게 반복 호출하는 통합 테스트나 시연 시 rate limit이 거슬릴 수 있습니다.

`.env`에 다음 한 줄 추가:

```dotenv
RATELIMIT_ENABLED=false
```

또는 IDE 실행 설정에 환경변수 `RATELIMIT_ENABLED=false` 추가. 재시작 시 `RateLimitFilter`가 모든 요청을 즉시 통과시킵니다.

**운영(`application-prod.yml`)에서는 항상 활성화**되어 있으며, env로 비활성화 불가합니다.

### 키 구조 (Redis)

- 형식: `ratelimit:{endpoint-id}:{client-ip}`
- 예: `ratelimit:signup:127.0.0.1`
- TTL = 정책 window. 만료 후 자동 삭제 → 다음 요청이 새 window 시작.

### 통합 테스트와의 격리

`AbstractIntegrationTest`가 기본적으로 `ratelimit.enabled=false`로 설정하여 같은 IP로 반복 호출하는 다른 통합 테스트가 자기 한도에 부딪히지 않게 합니다. Rate Limit 자체 동작은 `RateLimitIntegrationTest`가 `@DynamicPropertySource`로 별도 활성화하여 검증.

## 모니터링 (Actuator + Prometheus)

운영 메트릭 카탈로그는 [metrics-catalog.md](operations/metrics-catalog.md)에 정리되어 있습니다 (KAN-104).

### 로컬에서 확인

애플리케이션 실행 후 다음 endpoint 접속 가능:

- **Health**: <http://localhost:8080/actuator/health> — DB/Redis component UP/DOWN
- **Info**: <http://localhost:8080/actuator/info> — 빌드 정보
- **Prometheus**: <http://localhost:8080/actuator/prometheus> — 모든 메트릭 (text format)

### 메트릭 확인 예시

```bash
# JWT 검증 메트릭 (호출 후 호출 횟수 누적)
curl http://localhost:8080/actuator/prometheus | grep auth_jwt_verify_duration_seconds_count

# Rate Limit 판정 메트릭
curl http://localhost:8080/actuator/prometheus | grep ratelimit_decision_total

# 로그인 시도 메트릭
curl http://localhost:8080/actuator/prometheus | grep auth_login_attempt_total
```

### 미노출 endpoint

`env`/`beans`/`mappings`/`configprops` 등은 SecurityConfig에서 `denyAll` + yml `exposure.include`에서 제외 → 외부 정보 노출 차단. 디버깅이 필요해 추가 노출하려면 `application-local.yml`에 한정해 추가 후 운영 yml에는 절대 추가 금지.

### 운영 배포 전 필수

`/actuator/prometheus`는 본 PR에서 `permitAll`. 운영 배포 전 IP allowlist 또는 별도 management port 분리 필수. 상세는 [metrics-catalog.md](operations/metrics-catalog.md) § 노출 정책 참조.

## 보안 감사 로그 (KAN-105)

인증 도메인의 보안 이벤트(로그인/로그아웃/토큰 변조/rate limit 거부 등)는 별도 `audit.security` logger로 구조화 출력됩니다. 일반 application 로그와 분리되어 SIEM 수집/포렌식에 활용.

전체 이벤트 카탈로그 + 알람 룰 권장값: [audit-log-catalog.md](operations/audit-log-catalog.md)

### 로컬에서 확인

애플리케이션 실행 시 자동으로 `logs/audit-security.log` 파일 생성 (logstash JSON 포맷):

```bash
# tail로 실시간 추적
tail -f logs/audit-security.log

# 특정 eventType 필터
grep '"audit.eventType":"LOGIN_FAILURE"' logs/audit-security.log
```

회전 정책: 100MB 단위 + 일 단위 + 최대 365일 보존 + 10GB 전체 cap.

### 운영(prod) 출력 추가

prod 프로파일은 파일 + **stdout JSON** 동시 출력. ECS Task / CloudWatch Logs 수집기가 stdout을 캡처해 별도 인덱스로 수집.

### 민감 정보 금지

`SecurityAuditEvent` 자료형에 비밀번호 / JWT 본문 / Refresh Token 본문 / Cookie 값 필드 자체가 없음 → 컴파일 단계에서 노출 차단. `metadata` 맵에도 절대 넣지 말 것.

## 실행 방법

### 방법 1. 스크립트로 실행

macOS/Linux:

```bash
sh scripts/dev-up.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-up.ps1
```

스크립트는 `.env`가 없으면 자동으로 `.env.example`을 복사한 뒤 `docker compose up -d`를 실행합니다.

### 방법 2. Docker Compose 명령으로 실행

```bash
docker compose up -d
docker compose ps
```

`docker compose ps`에서 `chunbae-tour-mysql`, `chunbae-tour-redis`가 `Up` 또는 `healthy` 상태면 정상입니다.

## 애플리케이션 실행

Docker 컨테이너가 실행된 뒤 Spring Boot 애플리케이션을 실행합니다.

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

## 접속 정보

기본 설정 기준입니다.

| 항목 | 값 |
|---|---|
| MySQL Host | `localhost` |
| MySQL Port | `3307` |
| MySQL Database | `chunbae_tour` |
| MySQL Username | `chunbae` |
| MySQL Password | `1234` |
| Redis Host | `localhost` |
| Redis Port | `6380` |
| Redis Password | 빈 값 |

## 포트 충돌 해결

로컬 PC에서 이미 MySQL `3307` 포트를 사용 중이면 Docker 실행이 실패할 수 있습니다.

이 경우 `.env`에서 `DB_PORT`만 다른 값으로 바꿉니다.

```dotenv
DB_PORT=3308
```

Redis 포트가 충돌하면 `REDIS_PORT`를 바꿉니다.

```dotenv
REDIS_PORT=6381
```

변경 후 다시 실행합니다.

```bash
docker compose up -d
docker compose ps
```

Spring Boot `local` 프로필도 같은 `.env` 값을 읽기 때문에, `application-local.yml`을 직접 수정하지 않아도 됩니다.

## 종료 방법

### 컨테이너만 종료

macOS/Linux:

```bash
sh scripts/dev-down.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-down.ps1
```

또는 직접 실행합니다.

```bash
docker compose down
```

### 데이터를 포함해서 초기화

MySQL/Redis 데이터를 모두 지우고 처음 상태로 다시 시작하려면 볼륨까지 삭제합니다.

```bash
docker compose down -v
```

이 명령은 로컬 개발 DB 데이터를 삭제하므로 필요한 데이터가 있으면 먼저 백업하세요.

## 자주 겪는 문제

### `port is already allocated`

이미 다른 프로그램이 같은 포트를 사용 중입니다.

- MySQL이면 `.env`의 `DB_PORT`를 `3308` 같은 다른 값으로 변경합니다.
- Redis이면 `.env`의 `REDIS_PORT`를 `6381` 같은 다른 값으로 변경합니다.
- 변경 후 `docker compose up -d`를 다시 실행합니다.

### `Access denied for user`

기존 MySQL 볼륨에 이전 계정 정보가 남아 있을 수 있습니다.

로컬 데이터가 필요 없다면 다음 명령으로 볼륨을 초기화합니다.

```bash
docker compose down -v
docker compose up -d
```

### Docker 명령이 실행되지 않음

- Docker Desktop이 실행 중인지 확인합니다.
- Windows에서는 PowerShell을 새로 열어 다시 시도합니다.
- Docker Desktop 설치 직후라면 PC 재부팅이 필요할 수 있습니다.

## 설정 파일 역할

| 파일 | 역할 |
|---|---|
| `.env.example` | 팀원이 복사해서 사용할 환경변수 예시 |
| `.env` | 개인 로컬 환경변수 파일, Git에 커밋하지 않음 |
| `docker-compose.yml` | MySQL/Redis 컨테이너 실행 설정 |
| `src/main/resources/application-local.yml` | Spring Boot local 프로필 DB/Redis 접속 설정 |
| `scripts/dev-up.sh` | macOS/Linux 컨테이너 실행 스크립트 |
| `scripts/dev-up.ps1` | Windows PowerShell 컨테이너 실행 스크립트 |
| `scripts/dev-down.sh` | macOS/Linux 컨테이너 종료 스크립트 |
| `scripts/dev-down.ps1` | Windows PowerShell 컨테이너 종료 스크립트 |

## 시크릿 관리

운영 시크릿 주입 표준은 [ADR 0002 — Secret Injection Standard](adr/0002-secret-injection-standard.md)에 박제되어 있고, 전체 시크릿 목록 + 환경별 주입 방식 + 권장 회전 주기는 [시크릿 카탈로그](operations/secrets-catalog.md)에 정리되어 있습니다.

**환경별 주입 방식 요약**:

- **local**: `.env` 파일 (`.env.example` 복사). docker-compose가 env_file로 컨테이너에 주입.
- **staging/prod (현재)**: GitHub Actions Secret → 컨테이너 env var (Phase 1).
- **prod (ECS 전환 후)**: AWS Secrets Manager + ECS Task Definition `secrets[]` (Phase 2).

**신규 시크릿 추가 시**: [secrets-catalog.md §"신규 시크릿 추가 절차"](operations/secrets-catalog.md#신규-시크릿-추가-절차)를 따라 카탈로그 + `.env.example` + `application-prod.yml` + `SecretValidator` 4곳을 동기 갱신해야 합니다.

**부팅 시 검증**: prod 프로파일은 `SecretValidator`가 시크릿 카탈로그 기반 검증을 수행해 잘못된 값/placeholder가 운영에 노출되는 것을 차단합니다. 검증 실패 시 부팅 단계에서 `IllegalStateException`으로 즉시 실패합니다.

## 운영 배포 전 후속 작업

### Trusted Proxy / X-Forwarded-For allowlist 검증 (KAN-65 후속)

RateLimitFilter는 클라이언트 IP를 `request.getRemoteAddr()`로 추출한다. 로드밸런서/리버스 프록시 뒤에 배포되면 모든 요청이 LB IP 1개로 보이게 되어 rate limit bucket이 전 클라이언트 공유 상태가 된다.

**현재 적용된 최소 조치** (`application.yml`):

- `server.forward-headers-strategy: NATIVE` — Tomcat RemoteIpValve 활성화. `X-Forwarded-For` 헤더를 `getRemoteAddr()` 결과에 반영.
- Spring Boot 기본 `internal-proxies` 정규식은 RFC 1918 사설 IP 대역만 신뢰.

**운영 배포 전 반드시 확인할 항목**:

1. **LB IP 대역이 RFC 1918 사설 대역에 포함되는지 확인.** 사설 대역 밖이면 `server.tomcat.remoteip.internal-proxies` regex를 LB IP에 맞게 명시 설정 필요.
2. **Trusted proxy allowlist 정책 확정.** raw `X-Forwarded-For`를 allowlist 없이 신뢰하면 spoofing 위험 (클라이언트가 임의 IP 위장 → rate limit 우회).
3. **Spring `ForwardedHeaderFilter` 또는 동등 처리 검토.** scheme/host도 함께 신뢰해야 하면 별도 설정.
4. **배포 환경별 검증.** AWS ALB / K8s Ingress / 직접 Nginx 등 환경에 따라 헤더 흐름이 다름. 실제 환경에서 `X-RateLimit-*` 응답 헤더를 확인하며 클라이언트 IP가 정확히 분리되는지 검증.

**분리 사유**: 배포 환경(AWS ALB / K8s / 기타) 의존 결정 + ops 협의 필요. KAN-65 본 PR 범위 밖.

**참조**: `src/main/java/com/chunbaetour/domain/common/ratelimit/RateLimitFilter.java`의 `extractClientIp` Javadoc.
