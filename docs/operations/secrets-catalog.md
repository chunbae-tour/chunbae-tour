# Secrets Catalog (chunbae-tour)

> 운영/스테이징/로컬 환경에서 사용하는 시크릿/환경변수 카탈로그.
> 주입 방식 표준은 [ADR 0002](../adr/0002-secret-injection-standard.md) 참조.

## 환경별 주입 방식

| 환경 | Phase | 주입 방식 |
|---|---|---|
| **local** | - | `.env` 파일 (`.env.example` 복사). docker-compose가 컨테이너에 env_file로 주입. Spring `local` 프로파일이 `${ENV_VAR}` 참조 |
| **staging / prod (현재)** | Phase 1 | **GitHub Actions Secret → 컨테이너 env var**. workflow가 deploy job에서 `docker run -e KEY=$VAR` 또는 ECR push 후 EC2에서 `docker-compose --env-file` 패턴으로 주입 |
| **prod (ECS 전환 후)** | Phase 2 | **AWS Secrets Manager + ECS Task Definition `secrets[]`** 필드. 앱 코드 변경 0 — `${ENV_VAR}` 참조는 ECS가 Secrets Manager에서 가져와 채움 |

## 시크릿 카탈로그

### 인증 / 토큰

| 환경변수 | 용도 | 형식 / 길이 | 권장 회전 주기 | 누락 시 영향 | Phase 1 위치 | Phase 2 위치 |
|---|---|---|---|---|---|---|
| `JWT_SECRET` | JWT(HS256) 서명 키 | raw string, 32바이트 이상 | 90일 | 부팅 실패 (JwtProperties 검증) | GH Actions Secret | Secrets Manager |
| `JWT_ACCESS_TOKEN_TTL` | Access Token 만료 시간 (설정) | ISO-8601 Duration (예: `PT30M`) | 변경 시점 | 기본값 `PT30M` 사용 | yml default | yml default |
| `JWT_REFRESH_TOKEN_TTL` | Refresh Token 만료 시간 (설정) | ISO-8601 Duration (예: `P7D`) | 변경 시점 | 기본값 `P7D` 사용 | yml default | yml default |

> `JWT_*_TTL`은 시크릿이 아닌 설정값. 카탈로그에는 검증 대상이라 포함.

### 데이터베이스 / 캐시

| 환경변수 | 용도 | 형식 / 길이 | 권장 회전 주기 | 누락 시 영향 | Phase 1 위치 | Phase 2 위치 |
|---|---|---|---|---|---|---|
| `DB_HOST` | MySQL 호스트 (설정) | hostname | 인프라 변경 시 | 부팅 실패 | GH Actions Variable | Task Definition env |
| `DB_NAME` | MySQL 데이터베이스명 (설정) | identifier | 인프라 변경 시 | 부팅 실패 | GH Actions Variable | Task Definition env |
| `DB_USERNAME` | MySQL 사용자 (설정) | identifier | 90일 | 부팅 실패 | GH Actions Secret | Secrets Manager |
| `DB_PASSWORD` | MySQL 비밀번호 | 12자 이상 + 평문 디폴트 차단 | 90일 | 부팅 실패 (`SecretValidator`) | GH Actions Secret | **Secrets Manager + 자동 회전** |
| `REDIS_HOST` | Redis 호스트 (설정) | hostname | 인프라 변경 시 | 부팅 실패 | GH Actions Variable | Task Definition env |
| `REDIS_PASSWORD` | Redis 비밀번호 | string (비어있을 수 있음 — Redis 인증 미사용 시) | 90일 | 인증 실패 | GH Actions Secret | Secrets Manager |

> **운영 Redis 인증 정책 (가정 — 인프라 확정 시 갱신 필요)**:
> 운영 Redis는 외부 직접 접근이 차단된 사설 네트워크(VPC private subnet 등)에 위치한다고 가정한다.
> 이 가정 하에 비밀번호 인증을 사용하지 않으며, `REDIS_PASSWORD`는 `SecretValidator`의 prod 검증 대상에서 제외된다.
>
> 향후 멀티 리전 / VPC peering / 매니지드 캐시(ElastiCache 등) 도입으로 Redis가 외부 노출 또는 IAM-only 접근 모델로 바뀌면 본 가정을 재검토하고 `SecretValidator`에 검증 항목을 추가해야 한다.

### CORS / 보안 정책

| 환경변수 | 용도 | 형식 / 길이 | 권장 회전 주기 | 누락 시 영향 | Phase 1 위치 | Phase 2 위치 |
|---|---|---|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | CORS 허용 origin (콤마 구분) | `https://example.com,...` 형식. 와일드카드 금지 | 도메인 변경 시 | 부팅 실패 (`SecretValidator`) | GH Actions Variable | Task Definition env |

> CORS는 시크릿이 아닌 보안 정책. 카탈로그 포함 사유 = 부팅 검증 대상 + 환경별 분리 필수.

### Rate Limit

| 환경변수 | 용도 | 형식 / 길이 | 권장 회전 주기 | 누락 시 영향 | Phase 1 위치 | Phase 2 위치 |
|---|---|---|---|---|---|---|
| `RATELIMIT_ENABLED` | Rate Limit 토글 (설정) | `true` / `false`. prod는 `application-prod.yml`이 `true` 강제 | - | 기본값 `true` | yml override | yml override |

### 외부 API 키

| 환경변수 | 용도 | 형식 / 길이 | 권장 회전 주기 | 누락 시 영향 | Phase 1 위치 | Phase 2 위치 |
|---|---|---|---|---|---|---|
| `KAKAO_MAP_API_KEY` | Kakao Map REST API 키 | Kakao 발급 키 (placeholder `your-*` 차단) | Kakao 정책 / 유출 시 즉시 | 부팅 실패 (`SecretValidator`) + Map API 호출 실패 | GH Actions Secret | Secrets Manager |
| `PORTONE_SECRET` | PortOne V2 API Secret | PortOne 발급 (placeholder 차단) | PortOne 정책 / 유출 시 즉시 | 부팅 실패 + 결제 실패 | GH Actions Secret | **Secrets Manager** |
| `PORTONE_STORE_ID` | PortOne Store ID | `store-{uuid}` 형식 (placeholder 차단) | 변경 시점 | 부팅 실패 + 결제 실패 | GH Actions Secret | Secrets Manager |
| `PORTONE_CHANNEL_CARD` | PortOne 카드 결제 채널 키 | `channel-key-{uuid}` (placeholder 차단) | 변경 시점 | 부팅 실패 + 결제 실패 | GH Actions Secret | Secrets Manager |
| `PORTONE_CHANNEL_KAKAO_PAY` | PortOne 카카오페이 채널 키 | 동일 | 변경 시점 | 동일 | GH Actions Secret | Secrets Manager |
| `PORTONE_CHANNEL_TOSS_PAY` | PortOne 토스페이 채널 키 | 동일 | 변경 시점 | 동일 | GH Actions Secret | Secrets Manager |
| `PORTONE_CHANNEL_FOREIGN_CARD` | PortOne 해외 카드 채널 키 | 동일 | 변경 시점 | 동일 | GH Actions Secret | Secrets Manager |
| `PORTONE_WEBHOOK_SECRET` | PortOne Webhook 서명 검증 시크릿 | `whsec_*` 형식 (placeholder 차단) | PortOne 정책 / 유출 시 즉시 | 부팅 실패 + 결제 콜백 검증 실패 | GH Actions Secret | **Secrets Manager** |

## 신규 시크릿 추가 절차

1. **카탈로그 등록**: 본 문서에 한 줄 추가 (환경변수명, 용도, 형식, 회전 주기, Phase 1/2 위치).
2. **`.env.example` 갱신**: 로컬 더미 값(`your-*`, `xxx-*` 등 placeholder)으로 추가. 실제 값 절대 커밋 금지.
3. **`application-prod.yml` 갱신**: `${ENV_VAR}` 참조 추가. default 값 부여 금지 (부팅 시 누락 감지 위해).
4. **`SecretValidator` 갱신**: 형식 검증 추가 (예: placeholder 차단, 길이 체크).
5. **`SecretValidatorTest` 케이스 추가**: 누락 / 잘못된 값에 대한 부팅 실패 검증.
6. **GitHub Actions Secret 등록 (Phase 1)**: 리포지토리 `Settings → Secrets and variables → Actions`에서 추가. Production environment 사용 시 Production environment에 등록.
7. **(Phase 2 전환 시) AWS Secrets Manager 등록**: 시크릿 생성 + ECS Task Definition `secrets[]` 필드에 ARN 매핑 추가.

## 회전 절차 (수동)

> Phase 2 전환 후에는 회전 대상은 자동화 가능. Phase 1은 모두 수동.

1. **새 값 생성**: 시크릿 종류에 맞는 방식 (외부 API 키는 발급처 콘솔, JWT/DB 비번은 자체 생성).
2. **GitHub Actions Secret 업데이트**: `Settings → Secrets and variables → Actions`에서 값 교체.
3. **앱 재배포**: 새 값으로 컨테이너 재시작.
4. **이전 값 검증**: 일정 기간 후 이전 값으로 호출 시도 → 실패 확인 (외부 API 키는 발급처 콘솔에서 이전 키 폐기).
5. **회전 이력 기록**: 운영 변경 로그(별도 운영 채널 또는 ops 문서)에 회전 일시/대상/담당자 기록.

## 부팅 검증 (`SecretValidator`)

`prod` 프로파일에서만 활성화. 카탈로그의 검증 항목과 일치해야 함.

검증 실패 시 `IllegalStateException`으로 부팅 즉시 실패 + 명확한 메시지(`SECRET_VALIDATION: {시크릿명}: {사유}`) 출력. 잘못된 시크릿이 첫 요청까지 가지 않도록 차단.

세부 검증 정책은 [ADR 0002 §부팅 시 검증 정책](../adr/0002-secret-injection-standard.md#부팅-시-검증-정책-본-story-범위) 참조.

## 참조

- [ADR 0002 — 운영 Secret 주입 표준 (Phase 1/2 단계적 채택)](../adr/0002-secret-injection-standard.md)
- [Local Development Guide](../local-development.md)
- KAN-88 Story 본문 (`tmp/jira-drafts/_DONE_epic-b-s2-secret-injection-standard.md`)
- KAN-23 Auth foundation followup ("prod secret 주입 방식" 해소)
