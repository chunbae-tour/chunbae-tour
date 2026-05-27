# 에이전트 온보딩 가이드

이 문서는 춘배투어 프로젝트를 새로 맡는 에이전트가 먼저 읽고, 현재 저장소 구조와 도메인 방향을 빠르게 파악하기 위한 기준 문서입니다.

작업을 실제로 시작하는 에이전트는 먼저 [에이전트 최초 진입점](./agent-entrypoint.md)을 읽고, 필요한 기능별 하네스 안에서만 진행합니다.

## 프로젝트 한 줄 요약

춘배투어는 외국인 관광객과 한국인 사용자를 대상으로 하는 위치기반 관광지 매칭·투어 플랫폼입니다.

사용자의 현재 위치를 기준으로 주변 관광지, 전통시장, 축제 정보를 제공하고, 길찾기, 커뮤니티, 채팅, 신고, 리뷰, 엽전 결제, 관리자·상인 기능을 통해 실제 운영 가능한 여행 플랫폼을 목표로 합니다.

## 프로젝트 목표

- 외국인 관광객이 한국 관광지와 전통시장을 쉽게 찾고 이용할 수 있게 한다.
- 한국인 사용자가 외국인과 동행하거나 소통하며 여행 경험을 확장할 수 있게 한다.
- 관광 정보, 축제 일정, 커뮤니티, 채팅, 결제, 신고, 관리자 기능을 하나의 서비스 흐름으로 연결한다.
- 추후 Redis 기반 방문 이력을 활용해 이미 방문한 곳보다 새로운 장소와 맛집을 우선 추천하는 개인화 경험으로 확장한다.

## 핵심 Pain Point

- 외국인 관광객은 한국 관광지와 전통시장 정보를 한곳에서 찾기 어렵다.
- 현재 위치 기준으로 가까운 장소를 탐색하고 이동 경로를 확인하는 과정이 번거롭다.
- 언어와 문화 차이 때문에 현지 사람과 자연스럽게 소통하기 어렵다.
- 전통시장에서는 카드 수수료와 바가지 요금 문제로 결제 신뢰가 낮아질 수 있다.
- 상인, 사용자, 관리자가 함께 쓰는 운영 가능한 플랫폼 구조가 필요하다.

## MVP 포함 범위

- 회원가입, 로그인, 마이페이지, 찜
- 위치 기반 관광지·전통시장 탐색
- 길찾기, 카카오맵 API 연동
- 관광지·축제 전체 조회 및 캘린더
- 관광지 리뷰와 별점
- 인기 검색어
- 커뮤니티 게시글·댓글
- 신고
- 단체 채팅방과 실시간 메시지
- 동행 채팅방 참여 신청
- FAQ AI 답변
- 고객센터 실시간 상담 채팅
- 채팅방 메시지 번역
- 채팅 알림
- 엽전 충전·소비·결제
- 배너 광고
- 관리자 기능
- 상인 가게 관리와 상인 인증 마크

## MVP 제외 범위

다음 기능은 발표 이후 확장 기능으로 분리합니다.

- AI 개인 추천 코스
- 방문 이력 기반 개인화 추천
- 랜덤 매칭
- 날씨 구현
- 땅따먹기
- 뱃지, 쿠폰, 프로필 레벨
- 채팅 알림 외 결제·상인·관리자 알림
- AI 음성 기능, 어르신 모드, TTS

## 권한 구조

춘배투어 화면과 API는 일반 사용자, 상인, 관리자 영역으로 나뉩니다.

기본 Role은 다음과 같습니다.

| Role | 의미 | 주요 접근 범위 |
|---|---|---|
| `USER` | 일반 사용자 | 관광 탐색, 리뷰, 커뮤니티, 신고, 채팅, 엽전 결제 |
| `MERCHANT` | 승인된 상인 | 상인 페이지, 본인 가게와 메뉴 관리 |
| `ADMIN` | 관리자 | 유저, 상인 승인, 신고, 관광지, 축제, 광고 관리 |

상인 권한 흐름은 다음을 기준으로 합니다.

```text
일반 회원가입
-> USER 계정 생성
-> 상인 등록 신청
-> 관리자 승인
-> role이 MERCHANT로 변경
-> 상인 페이지 접근 가능
```

상인 본인용 API와 관리자 관리용 API는 분리합니다.

```text
상인 본인용: GET /api/v1/merchants/me/shop
관리자 관리용: GET /api/v1/admin/shops/{shopId}
관리자 관리용: PATCH /api/v1/admin/shops/{shopId}
```

## 현재 저장소 구현 상태

현재 로컬 저장소에는 인증·보안 기반 코드가 중심으로 구현되어 있습니다.

| 영역 | 현재 상태 |
|---|---|
| Spring Boot 애플리케이션 | `ChunbaeTourApplication` 존재 |
| 회원가입 | `SignupService`, `UserAuthController`, 테스트 존재 |
| 로그인 | `LoginService`, JWT 발급, 테스트 존재 |
| JWT 인증 | `TokenIssuer`, `JwtAuthenticationFilter`, 보안 핸들러 존재 |
| 공통 응답 | `ApiResponse` 존재 |
| 공통 예외 | `ErrorCode`, `BusinessException`, `GlobalExceptionHandler` 존재 |
| DB | MySQL, JPA 사용 |
| Redis | 의존성 및 로컬 Docker 구성 존재, 도메인 기능은 아직 본격 구현 전 |
| 문서 | 로컬 개발 환경 가이드 존재 |

## 현재 패키지 구조

```text
src/main/java/com/chunbaetour/domain
├── ChunbaeTourApplication.java
├── auth
│   ├── Account.java
│   ├── AccountRepository.java
│   ├── LoginService.java
│   ├── SignupService.java
│   ├── UserAuthController.java
│   ├── UserMeController.java
│   ├── config
│   ├── dto
│   ├── event
│   ├── jwt
│   └── security
└── common
    ├── config
    ├── error
    └── response
```

테스트는 다음 위치에 있습니다.

```text
src/test/java/com/chunbaetour/domain
├── ChunbaeTourApplicationTests.java
└── auth
    ├── LoginIntegrationTest.java
    ├── LoginServiceTest.java
    ├── PasswordHasherTest.java
    ├── SignupIntegrationTest.java
    ├── SignupServiceTest.java
    ├── jwt
    └── security
```

## 로컬 실행 기준

로컬 개발 환경은 `docs/local-development.md`를 기준으로 합니다.

주요 파일은 다음과 같습니다.

| 파일 | 역할 |
|---|---|
| `.env.example` | 팀원이 복사해서 사용할 환경변수 예시 |
| `.env` | 개인 로컬 환경변수 파일, Git 커밋 제외 |
| `docker-compose.yml` | MySQL, Redis 컨테이너 실행 설정 |
| `scripts/dev-up.ps1` | Windows 로컬 컨테이너 실행 |
| `scripts/dev-up.sh` | macOS/Linux 로컬 컨테이너 실행 |
| `scripts/dev-down.ps1` | Windows 로컬 컨테이너 종료 |
| `scripts/dev-down.sh` | macOS/Linux 로컬 컨테이너 종료 |

기본 실행 흐름은 다음과 같습니다.

```powershell
Copy-Item .env.example .env
.\scripts\dev-up.ps1
.\gradlew.bat bootRun
```

테스트 실행은 다음을 사용합니다.

```powershell
.\gradlew.bat test
```

## 구현 시 지켜야 할 구조

새 도메인은 `com.chunbaetour.domain.{domainName}` 아래에 둡니다.

권장 구조는 다음과 같습니다.

```text
src/main/java/com/chunbaetour/domain/{domainName}
├── {Aggregate}.java
├── {Aggregate}Repository.java
├── {Domain}Service.java
├── {Domain}Controller.java
├── dto
└── event
```

공통 규칙은 다음과 같습니다.

- 외부 API 응답은 `ApiResponse`를 사용합니다.
- 비즈니스 예외는 `BusinessException`과 `ErrorCode`를 우선 사용합니다.
- 인증된 사용자 식별은 JWT 인증 결과를 기준으로 전달합니다.
- 관리자 API와 사용자 API는 URL과 권한을 분리합니다.
- 다른 담당자의 도메인 엔티티를 직접 수정하기보다 서비스 메서드나 명확한 참조 ID로 경계를 유지합니다.
- MVP 제외 기능은 구현하지 말고 확장 지점만 남깁니다.

## 도메인 지도

| 도메인 | 주요 기능 | 비고 |
|---|---|---|
| Auth/User | 회원가입, 로그인, JWT 인증, 마이페이지 | 현재 구현 중심 |
| Tourism | 관광지, 전통시장, 위치 기반 탐색 | Redis Geospatial 연계 예정 |
| Festival | 축제·행사 조회, 캘린더 | 관광지와 연결 가능 |
| Review | 관광지 리뷰, 별점 | 사용자 인증 필요 |
| Community | 게시글, 댓글 | 신고와 연결 |
| Report | 게시글·댓글·리뷰·사용자 신고 | 관리자 처리와 연결 |
| Chat | 단체 채팅방, 실시간 메시지, 참여 신청 | WebSocket/STOMP |
| Notification | 채팅 알림 | MVP는 채팅 알림만 |
| Payment | 엽전 충전, 소비, 결제 | PG 연동 및 정산 정책 필요 |
| Merchant | 상인 등록 신청, 가게·메뉴 관리, 인증 마크 | 관리자 승인 필요 |
| Admin | 유저, 상인, 신고, 관광지, 축제, 광고 관리 | 관리자 API 분리 |
| Search | 인기 검색어 | Redis ZSet 예정 |
| AI/Support | FAQ AI 답변, 고객센터 상담 | MVP 범위 확인 필요 |

## API 작성 기준

기본 prefix는 `/api/v1`을 사용합니다.

예시는 다음과 같습니다.

```text
POST /api/v1/users/auth/signup
POST /api/v1/users/auth/login
GET  /api/v1/users/me/ping

GET  /api/v1/tourist-spots
GET  /api/v1/festivals
GET  /api/v1/festivals/calendar

GET  /api/v1/community/posts
POST /api/v1/community/posts
POST /api/v1/reports

GET  /api/v1/admin/reports
PATCH /api/v1/admin/reports/{reportId}
```

## 에이전트 작업 순서

새 작업을 시작할 때는 다음 순서로 확인합니다.

1. `docs/agent-entrypoint.md`를 읽고 최초 진입 절차를 확인합니다.
2. `docs/agent-onboarding.md`로 전체 프로젝트 방향을 확인합니다.
3. 담당 도메인 문서가 있으면 먼저 읽습니다.
4. `docs/agent-harness/global-guardrails.md`로 보호 파일과 보고 조건을 확인합니다.
5. 작업 요청에 맞는 기능별 하네스만 읽습니다.
6. `docs/local-development.md`로 로컬 실행 환경을 확인합니다.
7. 현재 코드의 패키지와 테스트 스타일을 확인합니다.
8. 기존 공통 응답, 예외, 보안 흐름에 맞춰 구현합니다.
9. 도메인 변경 후 관련 단위 테스트 또는 통합 테스트를 추가합니다.
10. `docs/agent-harness/work-report-template.md` 형식으로 결과를 보고합니다.

## 주의할 점

- 현재 문서의 일부 Notion 링크는 로컬 저장소에 원문이 없으므로, 로컬 문서와 사용자 지시를 우선합니다.
- `application-local.yml`은 Git에서 제외되어 있을 수 있으므로 로컬 환경 파일에 의존하는 변경은 문서와 `.env.example`을 함께 확인합니다.
- Redis 기반 기능은 위치 탐색, 인기 검색어, 방문 이력 등에서 쓰일 예정이지만 MVP와 추후 확장 범위를 구분해야 합니다.
- 엽전 결제는 서비스 핵심 차별점이지만 PG, 충전, 소비, 환불, 정산 정책이 엮이므로 별도 도메인 경계를 유지합니다.
