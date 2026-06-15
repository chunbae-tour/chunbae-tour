# ECS 컷오버 후 체크리스트 (Epic E — ECS 전환)

EC2 단일 호스트 → ECS Fargate + ALB 전환의 **컷오버(E9) 직후 운영 절차**.
인프라/콘솔 작업은 운영자(정민교) 직접, 코드 산출물은 PR.

> 관련: 설계 = `tmp/jira-drafts/E-epic-ecs-migration.md`(E0~E10), 배포 정책 = `docs/operations/deployment-migration-policy.md`, Flyway = `docs/operations/flyway-runbook.md`, 시크릿 = `docs/operations/secrets-catalog.md`.

## 현재 운영 구성 (전환 완료분)
| 항목 | 값 |
|---|---|
| Region / Account | ap-northeast-2 / 310133718863 |
| ECS Cluster / Service | `chunbae-cluster1` / `chunbae-service` (Fargate) |
| Task Definition | `chunbae-tour` (이미지 = ECR SHA 태그) |
| ALB / Target Group | `chunbae-alb` / `chunbae-tg` (target type=ip, 트래픽 8080, health 9090 `/actuator/health/lb`) |
| 앱 포트 / management 포트 | 8080 / 9090 (actuator 전용, `address=0.0.0.0`) |
| RDS | MySQL 8.4 (Flyway 부팅 시 자동 migrate) |
| ElastiCache | **Cluster Mode ENABLED** (clustercfg 엔드포인트, TLS=rediss://, AUTH 없음) |
| Secrets | Secrets Manager `chunbae-tour/prod` (24키 valueFrom 주입) |

## 1. 컷오버 직후 검증 (즉시)
- [ ] ALB DNS / `https://api.chunbae-tour.site` 로 스모크: **가입 · 로그인 · 결제** 정상
- [ ] ECS Target Group `chunbae-tg` healthy 1/1 (`/actuator/health/lb` = 200)
- [ ] CloudWatch `/ecs/chunbae-tour` 부팅 로그: Redis TLS · RDS 연결 성공, 예외 없음
- [ ] `flyway validate` 통과 — 앱 재배포 1회로 확인 (RDS의 `flyway_schema_history`가 덤프본과 정합)
- [ ] 운영 데이터가 RDS에 있음(가입/주문 등 핵심 테이블 row 수 대조)
- [ ] 태스크 강제 중지 → 자동 재기동 + ALB 5xx 최소(복구 동작) 확인
- [ ] Redis는 이전 없음 → **전 사용자 refresh 토큰 초기화 = 재로그인 1회** 발생함(사전 공지 확인)

## 2. ⚠️ 파이프라인 (E8 — 코드 PR 선행 필수)
**컷오버 후 CD가 아직 EC2 ssh 배포면, 다음 릴리즈가 죽은 EC2로 나가고 ECS는 갱신 안 됨.**
- [ ] `cd.yml`이 ECS 배포(`render-task-definition` + `deploy-task-definition`)로 전환됐는지 확인 (E8 PR)
- [ ] GHA Role에 ECS 배포 권한 + GH Secrets(`ECS_CLUSTER`/`ECS_SERVICE`) 추가됐는지
- [ ] E8 머지 후: main 릴리즈 1회 → ECS rolling 배포 자동 수행 + 배포 중 ALB 5xx 0건 확인

## 3. 안정화 관찰 (권장 2~3일)
- [ ] 에러율 / p95 레이턴시 / ECS 태스크 재기동 빈도 모니터링
- [ ] Redis(Lettuce) 관련 예외 추적 — 특히 `CROSSSLOT`(cache 워밍업/랭킹 RENAME, 스케줄러 시점). **@Async라 앱은 안 죽지만** 캐시 워밍업/일간 랭킹 초기화/통계 동기화가 조용히 실패함 → 별도 수정 트랙(아래 §6)
- [ ] 결제/웹훅(PortOne) 정상 처리 확인

## 4. EC2 정리 (안정화 확인 후)
- [ ] EC2 앱/MySQL/Redis 컨테이너 종료
- [ ] EC2 인스턴스 중지 → (며칠 더 관찰 후) 종료
- [ ] **EIP 해제** — 미사용 Elastic IP는 과금됨
- [ ] `prod.env` 등 **EC2 잔여 시크릿 파일 파기** (Secrets Manager로 이전 완료)
- [ ] cd.yml에서 `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY` GH Secret 제거(E8에서 미사용)
- [ ] EC2용 SG 22(SSH) 인바운드 규칙 정리

## 5. 롤백 절차
- ECS: **이전 task definition revision으로 `update-service`** 1커맨드 (이미지 SHA가 revision에 고정).
- 안정화 기간 동안 EC2를 즉시 종료하지 않는 이유 = 최후 폴백 보존. 단 데이터는 RDS가 정본이므로 EC2 회귀 시 데이터 동기화 주의.

## 6. 남은 후속 (별도 트랙)
- [ ] **CROSSSLOT 수정** (Cluster Mode 회귀, non-blocking): `CacheWarmupService`·`PopularSearchService`·`TypoCorrectionService`의 `rename()`, `PlaceStatsSyncChunkService`의 `multiGet()`이 cross-slot. hash tag(`{...}`) 적용 또는 rename 패턴 제거로 cluster-safe 전환. (PR 분할 설계 후 진행)
- [ ] **E10 S3 presigned 업로드** — 이미지 업로드 발급 API(별도 기능 PR, 안정화 후)
- [ ] Auto Scaling / Multi-AZ RDS / CloudWatch 대시보드 고도화 — 안정화 후

## 운영 메모
- actuator 9090은 SG(`app-sg` 9090 ← `alb-sg`)로만 노출. prometheus는 운영 미노출(모니터링 S8에서 관리포트 보안 갖춰 재도입).
- 루트 `/actuator/health`(show-details: always)는 전체 indicator(diskSpace/ssl 포함) 디버깅용, ALB는 `/actuator/health/lb`(db·redis·ping)로 게이팅.
