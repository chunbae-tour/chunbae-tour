# 전역 가드레일

이 문서는 박경화 담당 도메인 작업에서 모든 에이전트가 반드시 지켜야 하는 공통 하네스입니다.

## 최우선 원칙

| 원칙 | 내용 |
|---|---|
| 범위 제한 | 박경화 담당 도메인 밖으로 나가지 않습니다. |
| 보호 파일 제한 | `.env`, `application*.yml`, 인프라·빌드·보안 파일은 직접 수정하지 않습니다. |
| 직접 수정 금지 | Auth, Security, 타 도메인은 읽기만 합니다. Common은 사전 보고 후 최소 수정만 검토합니다. |
| 애매하면 질문 | 요구사항이 애매하면 추론하지 않고 먼저 보고합니다. |
| 큰 변경 전 보고 | 구조 변경, 연쇄 변경, 크리티컬 변경은 작업 전 보고합니다. |
| 결과는 표로 보고 | 사용자가 한눈에 확인할 수 있도록 표 중심으로 정리합니다. |

## 박경화 담당 도메인

직접 구현 가능한 담당 도메인은 다음뿐입니다.

| 도메인 | 포함 기능 |
|---|---|
| Community Post | 커뮤니티 게시글 |
| Comment | 게시글 댓글 |
| Report | 신고 접수와 처리 |
| Festival | 축제·행사 조회와 캘린더 |
| Review | 관광지 리뷰와 별점 |

## 수정 가능 범위

기능 구현 시 기본 수정 가능 범위는 다음입니다.

| 구분 | 경로 |
|---|---|
| 게시글 | `src/main/java/com/chunbaetour/domain/community/post/**` |
| 댓글 | `src/main/java/com/chunbaetour/domain/community/comment/**` |
| 신고 | `src/main/java/com/chunbaetour/domain/report/**` |
| 축제·캘린더 | `src/main/java/com/chunbaetour/domain/festival/**` |
| 관광지 리뷰 | `src/main/java/com/chunbaetour/domain/review/**` |
| 게시글 테스트 | `src/test/java/com/chunbaetour/domain/community/post/**` |
| 댓글 테스트 | `src/test/java/com/chunbaetour/domain/community/comment/**` |
| 신고 테스트 | `src/test/java/com/chunbaetour/domain/report/**` |
| 축제 테스트 | `src/test/java/com/chunbaetour/domain/festival/**` |
| 리뷰 테스트 | `src/test/java/com/chunbaetour/domain/review/**` |
| 담당 문서 | `docs/park-kyunghwa-domain.md`, `docs/agent-harness/**` |

기존 코드가 이미 다른 패키지 구조로 구현되어 있다면, 바로 수정하지 말고 현재 구조와 변경안을 보고합니다.

## 보호 파일

다음 파일은 보호 파일입니다.

| 파일 | 기본 규칙 |
|---|---|
| `.env` | 직접 수정 금지 |
| `.env.example` | 사전 보고 후 수정 |
| `src/main/resources/application.yml` | 사전 보고 후 수정 |
| `src/main/resources/application-*.yml` | 사전 보고 후 수정 |
| `src/main/resources/application-*.yaml` | 사전 보고 후 수정 |
| `src/main/resources/application-*.properties` | 사전 보고 후 수정 |
| `docker-compose.yml` | 사전 보고 후 수정 |
| `build.gradle` | 사전 보고 후 수정 |
| `settings.gradle` | 사전 보고 후 수정 |
| `.gitignore` | 사전 보고 후 수정 |
| `src/main/java/com/chunbaetour/domain/auth/**` | 직접 수정 금지 |
| `src/main/java/com/chunbaetour/domain/common/**` | 사전 보고 후 최소 수정 |

## 타 도메인 직접 수정 금지

다음은 박경화 담당 밖입니다.

| 도메인 | 예시 |
|---|---|
| Auth/User | 계정, 로그인, JWT, Security |
| Tourism | 관광지 기본 정보, 위치 기반 탐색 |
| Merchant | 상인, 가게, 메뉴 |
| Payment | 엽전, 충전, 소비, 결제 |
| Chat | 채팅방, 메시지, WebSocket |
| Notification | 알림 |
| Search | 인기 검색어 |
| Admin 전체 구조 | 관리자 공통 권한, 관리자 공통 라우팅 |

연동은 ID 참조로 시작합니다.

예시는 다음과 같습니다.

```text
authorId
reporterId
touristSpotId
targetType + targetId
```

다른 도메인 엔티티를 직접 연관관계로 묶어야 한다면 먼저 보고합니다.

## 보고 후 진행해야 하는 변경

| 변경 유형 | 보고해야 하는 이유 |
|---|---|
| 공통 에러코드 추가 | `common` 변경이며 모든 API에 영향 가능 |
| SecurityConfig 변경 | 접근 권한 전체에 영향 |
| JWT 인증 객체 변경 | 모든 인증 API에 영향 |
| DB 연관관계 변경 | 마이그레이션과 테스트 영향 |
| 새 의존성 추가 | 빌드와 배포 영향 |
| application 설정 변경 | 로컬·운영 환경 영향 |
| Docker 설정 변경 | 팀원 실행 환경 영향 |
| API prefix 변경 | 프론트엔드와 API 문서 영향 |
| 패키지 구조 변경 | 모든 에이전트 작업 경로 영향 |

## 애매하면 물어야 하는 예시

| 애매한 요청 | 물어볼 것 |
|---|---|
| "게시판 만들어줘" | 게시글만인지 댓글까지인지 |
| "신고 처리도 되게 해줘" | 신고 접수만인지 관리자 처리까지인지 |
| "캘린더 붙여줘" | 월별 조회 API인지 화면용 응답 구조인지 |
| "리뷰 연결해줘" | 관광지 엔티티가 있는지, ID 참조로 충분한지 |
| "권한 맞춰줘" | SecurityConfig 수정 승인 여부 |

## 작업 결과 보고 기준

작업 후에는 [작업 결과 보고 템플릿](./work-report-template.md)을 사용합니다.

테스트를 실행하지 못했다면 이유를 반드시 적습니다.

보호 파일을 건드리지 않았다면 "보호 파일 수정 없음"이라고 명시합니다.
