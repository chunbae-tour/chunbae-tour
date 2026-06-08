# ADR 0002 — 운영 Secret 주입 표준 (Phase 1/2 단계적 채택)

> Status: Accepted
> Date: 2026-05-21
> Context: KAN-88 (Epic KAN-64 운영 보안 인프라 S2)

## Context

운영 환경에서 사용하는 시크릿(`JWT_SECRET`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `KAKAO_MAP_API_KEY`, `PORTONE_*` 등)의 주입 방식을 표준화해야 한다.

현재 상태:

- `application-prod.yml`이 `${ENV_VAR}` 참조 형태로 시크릿을 요구하지만, 실제 주입 방식(누가 어떤 인프라로 환경변수를 채우는가)이 미정.
- `JWT_SECRET` 길이(32바이트)는 부팅 시 검증 중이나, 다른 시크릿(CORS origin 형식, DB 비밀번호 강도, PortOne/Kakao 키 형식)은 미검증 → 잘못된 값으로 부팅 시 첫 요청에서야 실패.
- KAN-23 Auth foundation 작업 중 followup 항목으로 보류된 사항. 운영 배포 전 결정 필수.

선택해야 할 사항:

1. **저장소**: 시크릿을 어디에 저장하는가 (GitHub Actions Secret / Vault / k8s Secret / AWS Secrets Manager / SaaS)
2. **주입 방식**: 컨테이너 env var 직접 vs 파일 마운트 vs API 호출
3. **회전 정책**: 수동 vs 자동
4. **부팅 시 검증 강도**: 어떤 시크릿을 얼마나 엄격하게 검증할 것인가

추가 제약:

- 배포 환경 = **현재 EC2, 향후 ECS 전환 가능성**. ADR은 양쪽 모두 호환되는 단계적 채택을 박제해야 함.
- 팀 규모 = 백엔드 코드 위주 + 전담 DevOps 없음. Vault/K8s 자체 운영 부담 과대.
- CI/CD = GitHub Actions 사용 예정.

## 결정

**Phase 1 (EC2 단계, 현재)**: **GitHub Actions Secret → 컨테이너 env var 주입** + **부팅 시 시크릿 검증 (`SecretValidator`)**.

**Phase 2 (ECS 전환 시, 후속)**: **AWS Secrets Manager + ECS Task Definition `secrets[]` 통합**. Phase 1과 호환되는 단계적 전환.

부팅 검증과 시크릿 카탈로그(`docs/operations/secrets-catalog.md`)는 본 Story 범위. 실제 Phase 2 전환은 별도 인프라 Epic.

## 근거

### 후보 비교

| # | 방식 | 운영 부담 | 비용 | 회전 자동화 | EC2 호환 | ECS 호환 | 권장 시점 |
|---|---|---|---|---|---|---|---|
| **1** | **GitHub Actions Secret → env var** ⭐ Phase 1 | 낮음 | 무료 | 수동 | ★★★ | ★★ | 소규모 + GH Actions 채택 |
| 2 | HashiCorp Vault | 높음 (HA, unseal, 백업) | OSS 무료 / 유료 | 강력 (동적 시크릿) | ★★ | ★★ | 보안 감사 요구 + 전담 DevOps |
| 3 | k8s Secret + Spring Boot Cloud Kubernetes | 중간 (K8s 운영 지식 필요) | 무료 | etcd 의존 | ✗ | ✗ (K8s 전제) | K8s 배포 확정 시 |
| **4** | **AWS Secrets Manager + ECS `secrets[]`** ⭐ Phase 2 | 낮음 | $0.40/시크릿/월 + API call | 자동 (Lambda 회전) | ★★ | ★★★ | ECS 전환 시 자연스러움 |
| 5 | Doppler / Infisical (SaaS) | 매우 낮음 | $7+/유저/월 | 자동 | ★★★ | ★★★ | 운영 인력 부족 + SaaS 비용 허용 |

### Phase 1 = GitHub Actions Secret 선택 사유

- **현재 CI/CD 기반과 정합**: GitHub Actions 채택 예정 → 별도 인프라 도입 없이 워크플로우 내에서 시크릿 주입 가능.
- **현재 코드와 정합**: `application-prod.yml`이 이미 `${ENV_VAR}` 참조. 주입 방식만 정하면 즉시 동작.
- **EC2 단계에서 추가 인프라 없음**: Vault 서버 띄우기, K8s 클러스터 구성, AWS Secrets Manager 비용 없이 시작 가능.
- **전담 DevOps 없는 팀에 적합**: GH Actions Secret UI = 직관적. 새 시크릿 추가는 PR 리뷰어 1명 + GH 권한자 1명이면 완료.
- **단순함 = 사고 가능성 낮음**: 시크릿 분실, 회전 실수, 미들웨어 장애로 인한 부팅 실패 시나리오가 가장 적음.

### Phase 2 = AWS Secrets Manager 채택 사유 (ECS 전환 시점)

- **ECS Task Definition `secrets[]` 필드 표준 통합**: ECS에서 컨테이너 환경변수에 Secrets Manager 시크릿을 직접 주입하는 게 native pattern. 별도 Sidecar/Init container 불필요.
- **자동 회전 지원**: DB 비밀번호처럼 자동 회전이 유의미한 시크릿에 Lambda 회전 함수 적용 가능. JWT_SECRET 등도 회전 자동화 가능.
- **감사 로그**: CloudTrail이 시크릿 접근 로그 제공 → 운영 보안 감사 표준(Epic B S4) 자료로 활용.
- **단계적 전환**: Phase 1의 `${ENV_VAR}` 참조 코드가 그대로 호환됨. ECS Task Definition에서 env var 대신 secrets 필드로만 전환하면 됨. **앱 코드 변경 0**.

### Phase 2 미리 채택하지 않는 사유

- **현재 단계에서는 인프라 비용 정당화 어려움**: 시크릿 ~10개 × $0.40 = 월 $4 + API call. EC2 단계에 추가하는 비용/복잡도 가치 < GH Actions Secret 단순함.
- **EC2에서 Secrets Manager 직접 호출은 부자연스러움**: SDK 또는 SSM Agent 추가 필요. ECS native 통합과 달리 추가 코드/IAM 설정 부담.
- **회전 자동화는 현재 단계 over-engineering**: 시크릿 회전을 매뉴얼로도 충분히 제어 가능 (시크릿 수 적음 + 변경 빈도 낮음).

### Phase 2 = Vault 또는 K8s Secret이 아닌 사유

- **Vault**: 동적 시크릿/감사 강점은 매력적이지만 자체 운영 부담(HA, unseal key, 백업)이 팀 규모에 과대. AWS Secrets Manager가 자동 회전 강점 대부분을 매니지드로 제공.
- **K8s Secret**: K8s 클러스터 전제. 현재 ECS 전환 시나리오와 정합하지 않음. K8s 도입이 결정되면 별도 ADR로 재검토.

## Trade-offs (수용한 부담)

- **Phase 1 회전은 수동**: 시크릿 회전 자동화 없음. 카탈로그에 권장 주기만 명시. 운영자가 캘린더로 추적 필요. → 회전 빈도가 낮은 시크릿(90일+) 기준이라 운영 부담 허용.
- **GH Actions Secret 감사 로그 약함**: Audit log가 GitHub 조직 플랜 의존. CloudTrail 수준 감사 불가. → 본격 감사 필요해지면 Phase 2 전환 트리거.
- **Phase 2 전환 시점에 IAM/네트워크 설계 필요**: ECS Task IAM Role + Secrets Manager 권한 + VPC endpoint 등. → 인프라 Epic에서 처리. ADR 코드 변경 없음.
- **시크릿 카탈로그 수동 동기화**: yml/.env.example/카탈로그 3곳에 시크릿 등록. → 부팅 검증(`SecretValidator`)으로 정합성 강제 + 신규 추가 절차 문서화로 완화.

## 후속 변경 트리거

다음 상황이면 Phase 전환 또는 다른 방식 재평가:

- **ECS 전환 결정**: Phase 2 (AWS Secrets Manager) 채택 시점.
- **보안 감사 요구 강화**: CloudTrail 수준 감사 로그가 컴플라이언스 요구로 등장하면 Phase 2 우선 진행 또는 Vault 재검토.
- **시크릿 회전 자동화 필요**: DB 비번 자동 회전이 인시던트 대응 요구로 등장하면 AWS Secrets Manager Lambda 회전 도입.
- **K8s 도입 결정**: 별도 ADR로 k8s Secret 또는 외부 시크릿 솔루션(External Secrets Operator + AWS Secrets Manager 백엔드) 재평가.
- **시크릿 수 폭증**: 현재 ~10개. 50+로 증가하면 카탈로그 관리 자동화 또는 Doppler/Infisical SaaS 재검토.

## 부팅 시 검증 정책 (본 Story 범위)

`SecretValidator` 컴포넌트가 prod 프로파일에서만 활성화되어 카탈로그 기반 검증 수행. 검증 실패 시 `IllegalStateException`으로 부팅 즉시 실패 (잘못된 시크릿이 첫 요청까지 가지 않음).

검증 항목:

| 시크릿 | 검증 |
|---|---|
| `JWT_SECRET` | 32바이트 이상 (기존 `JwtProperties` compact ctor가 검증, 재검증 안 함) |
| `CORS_ALLOWED_ORIGINS` | 비어있지 않음 + 콤마 구분 URL 형식 + 와일드카드(`*`) 차단 |
| `DB_PASSWORD` | 비어있지 않음 + 최소 길이 12자 + 평문 디폴트(`1234`, `password`, `root`) 차단 |
| `DB_USERNAME` | 비어있지 않음 + 디폴트 계정명(root, admin, administrator, test, qwerty, password 등) 차단 + placeholder 패턴(`your-`, `replace-me` 등) 차단 |
| `KAKAO_MAP_API_KEY` | 비어있지 않음 + placeholder 패턴(`your-`, `xxx`, `replace-me`) 차단 |
| `PORTONE_SECRET` | 비어있지 않음 + placeholder 패턴 차단 |
| `PORTONE_STORE_ID` | 비어있지 않음 + placeholder 패턴 차단 |
| `PORTONE_CHANNEL_*` (4종) | 비어있지 않음 + placeholder 패턴 차단 |
| `PORTONE_WEBHOOK_SECRET` | 비어있지 않음 + placeholder 패턴 차단 |

local 프로파일은 검증 비활성화 → `.env.example` 더미 값으로 부팅 가능.

## 참조

- sa-docs/11 운영 보안 정책
- docs/operations/secrets-catalog.md — 시크릿 카탈로그 (회전 주기 + 환경별 주입 방식)
- docs/local-development.md §"운영 배포 전 후속 작업"
- KAN-23 Auth foundation followup: "prod secret 주입 방식" → 본 ADR로 해소
- KAN-88 Story 본문 (`tmp/jira-drafts/_DONE_epic-b-s2-secret-injection-standard.md`)
