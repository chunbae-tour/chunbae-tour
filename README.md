# Chunbae Tour Backend

춘배투어 백엔드는 관광지, 지도, 검색, 축제, 전통시장, 동행 커뮤니티, 채팅, 상점, 결제, 엽전, 운영 관리 기능을 제공하는 Spring Boot 기반 API 서버입니다.

이 저장소는 서비스의 백엔드 애플리케이션 코드, DB 마이그레이션, Docker 실행 환경, ECS 배포 설정, 테스트 코드를 포함합니다.

## API 문서

- API 명세서: https://chunbae-tour-api.netlify.app/
- 운영 API Base URL: `https://api.chunbae-tour.site`
- 로컬 API Base URL: `http://localhost:8080`

운영 환경에서는 Swagger UI와 API Docs가 비활성화되어 있으므로, API 확인은 Netlify 문서를 기준으로 합니다.

## 백엔드 역할

```text
Frontend / Client
  -> REST API
  -> WebSocket

Spring Boot Backend
  -> 인증/인가
  -> 도메인 비즈니스 로직
  -> MySQL 조회/저장
  -> Redis 캐시/랭킹/락/PubSub
  -> 외부 API 연동
  -> 결제 웹훅 처리
  -> 운영 로그/메트릭 노출
```

## 기술 스택

### Application

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| API | Spring Web MVC |
| Realtime | Spring WebSocket |
| Security | Spring Security, JWT, OAuth(Kakao/Naver) |
| Persistence | Spring Data JPA, Hibernate Spatial, QueryDSL |
| Migration | Flyway |

### Data / Infra

| 구분 | 기술 |
| --- | --- |
| RDB | MySQL (local: 8.4, prod: RDS MySQL) |
| Cache / Ranking / Lock | Redis (local: 7, prod: ElastiCache), Redisson |
| Scheduler Lock | ShedLock |
| File Storage | AWS S3, Presigned URL |
| Deploy | Docker, AWS ECR, ECS Fargate, ALB |
| Secret | AWS Secrets Manager |
| Monitoring | Actuator, Micrometer Prometheus, ADOT, CloudWatch |
| Test | JUnit 5, Spring Boot Test, Testcontainers |

### External APIs

| 연동 | 사용처 |
| --- | --- |
| Kakao Local API | 주소 검색, 좌표 변환, 주변 상점 검색 |
| Kakao Map Link | 길찾기 링크 생성 |
| 한국관광공사 Tour API / KorService2 | 관광지 데이터 동기화 |
| 공공데이터포털 | 축제/전통시장 데이터 동기화 |
| PortOne V2 | 충전, 취소, 환불, QR 결제 |
| Google Translation API | 번역 |

## 패키지 구조

현재 `src/main/java/com/chunbaetour/domain` 아래에는 21개 도메인 패키지가 있습니다. 새 도메인을 추가하면 이 목록도 함께 갱신합니다.

```text
src/main/java/com/chunbaetour/domain
├── admin              # 관리자, 감사 로그, 대시보드, 심사/제재
├── auth               # 사용자/상인/관리자 인증, JWT, OAuth, 마이페이지
├── banner             # 배너
├── chat               # WebSocket 채팅, 참여 신청, 파일 업로드
├── common             # 공통 응답, 예외, 설정, Redis, S3, Rate Limit
├── community          # 자유/동행 게시글, 댓글, 이미지
├── companionreview    # 동행 생성/참여/종료/리뷰
├── cs                 # FAQ, 1:1 상담
├── festival           # 축제, 달력, 축제 동기화, 축제 찜
├── like               # 공통 찜
├── market             # 전통시장, 전통시장 동기화, 시장 찜
├── merchant           # 상인 입점 신청
├── notification       # 알림
├── payment            # 충전, 취소, 환불, QR 결제, 웹훅
├── place              # 관광지, 지도, 추천, 리뷰, 카카오 API 연동
├── report             # 신고, 제재
├── search             # 통합 검색, 자동완성, 인기/최근 검색어, 오타 교정
├── shop               # 가게, 메뉴, 공지, 이미지, 정산, 광고, QR
├── store              # 상품, 주문, 아이템
├── translation        # 번역
└── yeopjeon           # 엽전 지갑/거래내역
```

## 주요 도메인

### Auth / User

- 일반 사용자, 상인, 관리자 로그인
- Access Token / Refresh Token 재발급
- Kakao/Naver OAuth 로그인
- 마이페이지 홈, 프로필, 찜, 리뷰, 탈퇴
- 관리자 사용자 제재

### Place / Map / Recommend

- 관광지 목록/상세
- 지도 마커 일괄 조회
- 내 주변 관광지 조회
- 관광지 주변 맛집/상점 추천
- Kakao 지오코딩, 리버스 지오코딩, 길찾기
- Tour API 관광지 동기화
- Redis 캐시와 write-behind 통계 동기화

### Search

- 통합 검색
- 관광지/축제 전용 검색
- 자동완성
- 오타 교정
- 인기 검색어
- 최근 검색어
- 검색어 집계 제외 정책

### Festival / Market

- 축제 목록, 상세, 달력, 찜
- 전통시장 위치 기반 조회, 상세, 찜
- 공공데이터 기반 축제/전통시장 동기화
- 관리자 수동 동기화

### Community / Chat / Companion

- 자유/동행 게시글
- 댓글/대댓글
- 동행 생성, 참여자 관리, 종료
- WebSocket 채팅
- Redis Pub/Sub 메시지 전파
- S3 파일 업로드

### Shop / Merchant

- 상인 입점 신청
- 내 가게 관리
- 메뉴, 공지, 이미지
- 공개 가게 정보
- 정산 계좌, 지갑, 정산 신청
- 광고 신청/심사
- QR 코드

### Payment / Store / Yeopjeon

- PortOne 충전, 취소, 환불
- QR 결제
- 웹훅
- 결제 멱등성 처리
- 엽전 지갑
- 상품, 주문, 아이템

## 공통 설계

### API Response

공통 응답 포맷은 `ApiResponse`를 사용합니다.

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {}
}
```

### Error Handling

- `BusinessException`
- `ErrorCode` (`com.chunbaetour.domain.common.error.ErrorCode`)
- `GlobalExceptionHandler`

도메인 예외는 공통 예외 핸들러에서 HTTP status와 에러 코드로 변환됩니다.
대표 공통 에러 코드는 `INVALID_REQUEST(COMMON_002)`, `CONCURRENT_UPDATE(COMMON_009)`, `FILE_TOO_LARGE(COMMON_012)`입니다.

### Authentication Principal

인증된 사용자 ID는 컨트롤러에서 `@AuthenticationPrincipal Long userId`로 주입받습니다. 일부 공개 API는 optional auth로 동작해, 로그인한 경우에만 `isLiked` 같은 사용자별 값을 추가합니다.

## Redis 사용 지점

| 기능 | 자료구조 / 방식 | 목적 |
| --- | --- | --- |
| 인기 검색어 | ZSet | score 기반 TOP N 조회 |
| 최근 검색어 | List | 사용자별 최신 검색어 유지 |
| 주변 관광지 | Geo | 좌표 기반 반경 조회 |
| 관광지 상세 | Cache | DB 부하 감소 |
| 조회수/좋아요 | Counter + Dirty Set | Redis 선반영 후 DB 동기화 |
| 채팅/알림 | Pub/Sub | 인스턴스 간 메시지 전파 |
| Rate Limit | String/Counter | endpoint별 요청 제한 |
| 분산 락 | Redisson Lock | 캐시 스탬피드와 중복 처리 방어 |

Redis Cluster 환경에서는 multi-key 명령의 CROSSSLOT 문제를 피하기 위해 hash tag와 개별 GET/파이프라인 전략을 구분해 사용합니다.
예를 들어 인기 검색어 일간 스냅샷은 현재 랭킹 `search:ranking`과 같은 slot을 사용하도록 이전 랭킹 키를 `{search:ranking}:prev`로 둡니다.

## 스케줄러 / 배치성 작업

| 작업 | 주기 | 목적 |
| --- | --- | --- |
| 인기 검색어 일간 스냅샷 | 매일 00:00 (`search.ranking.reset-cron`) | 오늘 랭킹과 전일 랭킹 비교 |
| 관광지 통계 동기화 | 1분 fixedDelay | Redis 조회수/좋아요 값을 DB로 반영 |
| 관광지 데이터 동기화 | 매일 04:00 (`tour-api.kor-service.place-sync-cron`) | 한국관광공사 API 데이터 수집 |
| 축제 데이터 동기화 | 매일 02:00 (`tour-api.sync-cron`) | 공공데이터 축제 정보 수집 |
| 전통시장 동기화 | 매월 1일 02:00 | 공공데이터 전통시장 정보 수집 |
| 환불 재시도 | 60초 fixedDelay | 실패한 환불 처리 재시도 |

다중 인스턴스 환경에서 중복 실행되면 안 되는 작업은 ShedLock을 사용합니다.

## 로컬 개발

### 요구사항

- Java 21
- Docker Desktop
- Git

### 환경 변수 준비

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

### MySQL / Redis 실행

```powershell
docker compose up -d
```

MySQL과 Redis는 컨테이너 시작 직후 초기화 시간이 필요합니다. 다음 명령으로 `healthy` 상태를 확인한 뒤 서버를 실행합니다.

```powershell
docker compose ps
```

`.env.example`을 `.env`로 복사한 경우의 포트입니다. `.env` 없이 `docker compose up -d`만 실행하면 `docker-compose.yml`의 fallback 값에 따라 MySQL은 `3308`을 사용합니다.

| 서비스 | 주소 |
| --- | --- |
| MySQL | `localhost:3307` |
| Redis | `localhost:6380` |
| API | `http://localhost:8080` |

### 서버 실행

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew bootRun
```

### 테스트

macOS/Linux:

```bash
./gradlew test
./gradlew compileTestJava
```

Windows PowerShell:

```powershell
.\gradlew test
.\gradlew compileTestJava
```

## CI/CD

### CI

- 대상: PR to `develop`, PR to `main`, push to `develop`
- JDK 21 설정
- `./gradlew build -x test`
- `./gradlew compileTestJava`
- `./gradlew test`로 Testcontainers 통합 테스트 실행

### CD

- 대상: push to `main`
- `./gradlew test` gate 실패 시 배포 중단
- Docker image build
- ECR push
- ECS task definition 렌더링
- ECS Fargate rolling 배포
- ECS 서비스 안정화 대기와 ALB health check 통과 확인

`main` 브랜치로 머지되면 운영 ECS 서비스로 자동 배포됩니다.

## 운영 체크포인트

- DB 변경은 Flyway migration으로 관리합니다.
- 공유 DB에 적용된 migration은 수정하지 않고 새 migration으로 보정합니다.
- Redis Cluster에서는 multi-key 명령의 slot 제약을 고려합니다.
- 운영 Secret은 AWS Secrets Manager에서 주입합니다.
- Actuator는 운영에서 관리 포트 `9090`으로 분리됩니다.
- `/actuator/health/lb`는 ALB health check 기준으로 사용합니다.
- `/actuator/prometheus` 메트릭은 ADOT/Prometheus 수집 대상으로 사용하며, 응답 시간, 에러율, DB/Redis 상태를 우선 확인합니다.
- 애플리케이션 로그는 CloudWatch Logs에서 확인합니다.
- 운영 Swagger는 비활성화되어 있으므로 API 문서는 Netlify 명세를 기준으로 합니다.

## License

이 프로젝트는 MIT License를 따릅니다. 자세한 내용은 [LICENSE](LICENSE)를 참고하세요.
