# ECS Fargate Task Definition (Epic E — ECS 전환, Story E4)

`task-definition.json` = ECS Fargate 태스크 정의. **콘솔/CLI 등록은 민교 직접** (이 PR은 코드 산출물만).

## 등록 전 반드시 치환할 placeholder 2개

| placeholder | 무엇 | 상태 |
|---|---|---|
| ~~`<SECRET_ARN>`~~ | Secrets Manager 시크릿 ARN | ✅ 주입 완료: `arn:...:secret:chunbae-tour/prod-DFWHK4` |
| `image: ...:<IMAGE_TAG>` | 컨테이너 이미지 태그 | E8 CD가 `ecs-render-task-definition`으로 SHA 태그 주입. 수동 등록 시 ECR 빌드 SHA로 교체. **일부러 비유효 placeholder** — 치환 누락 시 mutable `latest`로 엉뚱한 이미지를 당기지 않고 즉시 실패하게 함(재현성). |

`<SECRET_ARN>` 예시 형식:
```text
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

## secrets 키 = 24개 (시크릿 실제 키와 대조 완료)

`secrets` 배열 = Secrets Manager에 실제 입력된 키 중 **앱이 env로 받아야 하는 24개**.
(시크릿 자체엔 더 많은 키가 있으나 아래 항목은 Task Definition에 주입하지 않는다 — 무해.)

**주입 안 함**:
- `SPRING_PROFILES_ACTIVE` → `environment`로 `prod` 리터럴 주입(민감정보 아님, Dockerfile ENV에도 고정). secrets+environment 중복 정의는 ECS가 거부하므로 environment 한 곳만.
- `DB_PORT` / `REDIS_PORT` — prod.yml에 3306/6379 하드코딩
- `REDIS_PASSWORD` — ElastiCache AUTH 미설정(TLS만). 비번 없음

**앱은 받지만 시크릿에 없는 키**(yml 기본값으로 동작 — 주입 불필요):
`JWT_ACCESS_TOKEN_TTL`(PT30M), `JWT_REFRESH_TOKEN_TTL`(P7D), `COOKIE_SAMESITE`(Lax), `REPORT_AUTO_HIDE_THRESHOLD`(3).
운영에서 이 값을 바꾸려면 시크릿에 키 추가 후 secrets 배열에 1줄 추가.

주입 24키: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD, REDIS_HOST, JWT_SECRET,
CORS_ALLOWED_ORIGINS, ACCOUNT_ENCRYPTION_KEY, PORTONE_SECRET, PORTONE_WEBHOOK_SECRET,
PORTONE_STORE_ID, PORTONE_CHANNEL_CARD, PORTONE_CHANNEL_KAKAO_PAY, PORTONE_CHANNEL_TOSS_PAY,
PORTONE_CHANNEL_FOREIGN_CARD, GOOGLE_TRANSLATION_API_KEY, KAKAO_MAP_API_KEY,
KAKAO_LOGIN_REST_API_KEY, KAKAO_LOGIN_CLIENT_SECRET, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET,
TOUR_API_SERVICE_KEY, TOUR_API_KOR_SERVICE_KEY, PUBLIC_DATA_MARKET_KEY.

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
