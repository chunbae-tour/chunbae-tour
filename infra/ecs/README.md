# ECS Fargate Task Definition (Epic E — ECS 전환, Story E4)

`task-definition.json` = ECS Fargate 태스크 정의. **콘솔/CLI 등록은 민교 직접** (이 PR은 코드 산출물만).

## 등록 전 반드시 치환할 placeholder 2개

| placeholder | 무엇 | 출처 |
|---|---|---|
| `<SECRET_ARN>` | Secrets Manager 시크릿 ARN (전체, 랜덤 6자 suffix 포함) | E3에서 생성한 `chunbae-tour/prod` 시크릿 ARN |
| `image: ...:latest` | 컨테이너 이미지 태그 | E8 CD가 `ecs-render-task-definition`으로 SHA 태그 주입 — 수동 등록 시엔 ECR 최신 SHA로 교체 |

`<SECRET_ARN>` 예시 형식:
```
arn:aws:secretsmanager:ap-northeast-2:310133718863:secret:chunbae-tour/prod-aB3xZ9
```
valueFrom 문법 `<ARN>:KEY::` = `시크릿ARN : JSON키 : (버전스테이지 공란) : (버전ID 공란)`.
JSON 형식 시크릿이라 키별로 `:KEY::` 접미사로 개별 주입된다.

치환 명령 예:
```bash
sed 's|<SECRET_ARN>|arn:aws:secretsmanager:ap-northeast-2:310133718863:secret:chunbae-tour/prod-aB3xZ9|g' \
  infra/ecs/task-definition.json > /tmp/td.json
aws ecs register-task-definition --cli-input-json file:///tmp/td.json --region ap-northeast-2
```

## ⚠️ secrets 키 = 28개 (시크릿 실제 27개와 대조 필요)

현재 배열은 `application.yml`/`application-prod.yml`의 `${ENV}` placeholder에서 역산한 **28개**다.
시크릿엔 27개라 했으니 **1개 초과** — 등록 전 1:1 대조 필수.

- 존재하지 않는 키를 secrets에 두면 → 태스크가 `ResourceNotFoundException`으로 기동 실패.
- 앱이 부팅에 필요한 키가 빠지면 → fail-fast 부팅 실패(기본값 없는 키).

**의도적으로 제외한 항목**(prod에서 env 불필요):
- `DB_PORT` / `REDIS_PORT` — prod.yml에 3306/6379 하드코딩
- `REDIS_PASSWORD` — ElastiCache AUTH 미설정(TLS만). 비번 없음 → 주입 불필요
- `RATELIMIT_ENABLED` — prod.yml에 `enabled: true` 리터럴
- `PUBLIC_DATA_MARKET_URL` — yml에 기본 URL 있음(키만 주입)

**기본값 있어 시크릿에서 빠졌을 후보**(빠졌으면 secrets 배열에서도 제거):
`JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`, `COOKIE_SAMESITE`, `REPORT_AUTO_HIDE_THRESHOLD`, `PUBLIC_DATA_MARKET_KEY`(전통시장 기능 미사용 시).

→ 실제 27개 키 이름을 확인해 배열을 정확히 맞춰주세요(키 이름은 비밀 아님).

## 포트 매핑 8080 + 9090

- `8080`: 앱 트래픽(ALB Target Group forward 대상).
- `9090`: actuator(health/prometheus). ALB TG health check가 태스크 ENI IP의 `9090/actuator/health`를 친다.
  - 이 때문에 `application-prod.yml`의 `management.server.address`를 `127.0.0.1` → `0.0.0.0`으로 변경(같은 PR).
  - 외부 노출 차단은 `app-sg`가 9090을 `alb-sg`에서만 허용하는 SG 규칙으로 유지(E0/E5).

## CloudWatch 로그 그룹 `/ecs/chunbae-tour`

`awslogs-create-group: "true"`로 첫 태스크 기동 시 **자동 생성** — 수동 생성 불필요.
(Execution Role에 `logs:CreateLogGroup`/`CreateLogStream`/`PutLogEvents` 권한 전제 — 권한 있다고 확인됨.)

## JVM 메모리

`-XX:MaxRAMPercentage=70.0`은 **Dockerfile의 `JDK_JAVA_OPTIONS` ENV**가 단일 출처로 관리(이미지에 고정).
태스크 정의에서 중복 지정하지 않음 — 변경 필요 시 Dockerfile 한 곳만 수정.

## Flyway

현행 "앱 부팅 시 Flyway 자동 migrate" 유지(epic E8 결정). `spring-boot-flyway` 모듈 + `flyway.enabled=true`.
desired=2여도 Flyway DB 락으로 경합 안전. 단독 migrate one-off task 분리는 후속(E8 주석 참조).

## graceful shutdown

`stopTimeout: 30` = graceful 20s(`spring.lifecycle.timeout-per-shutdown-phase`) + 여유.
Dockerfile exec-form ENTRYPOINT로 SIGTERM이 PID1(JVM)에 직접 전달 → graceful 실동작(KAN-226).
ALB deregistration delay(드레인)는 30s 권장(E5).
