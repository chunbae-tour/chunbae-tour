# 에이전트 하네스 라우터

이 디렉터리는 박경화 담당 도메인 작업을 기능별로 격리하기 위한 하네스 모음입니다.

에이전트는 필요한 하네스만 읽고 작업합니다. 여러 기능을 한 번에 바꾸는 작업은 먼저 범위를 보고해야 합니다.

## 공통 규칙

모든 기능 하네스는 [전역 가드레일](./global-guardrails.md)을 우선합니다.

전역 가드레일과 기능 하네스가 충돌하면 전역 가드레일을 따릅니다.

## 하네스 목록

| 하네스 | 담당 기능 | 기본 수정 가능 루트 |
|---|---|---|
| [Community Post](./community.md) | 커뮤니티 게시글 | `domain/community/post/**` |
| [Comment](./comment.md) | 게시글 댓글 | `domain/community/comment/**` |
| [Report](./report.md) | 신고 접수·처리 | `domain/report/**` |
| [Festival](./festival.md) | 축제·행사·캘린더 | `domain/festival/**` |
| [Review](./review.md) | 관광지 리뷰·별점 | `domain/review/**` |

테스트 루트는 운영 코드와 같은 기능 경계를 따릅니다.

```text
src/test/java/com/chunbaetour/domain/community/post/**
src/test/java/com/chunbaetour/domain/community/comment/**
src/test/java/com/chunbaetour/domain/report/**
src/test/java/com/chunbaetour/domain/festival/**
src/test/java/com/chunbaetour/domain/review/**
```

## 다중 기능 작업 규칙

둘 이상의 하네스가 필요한 요청은 다음 기준으로 나눕니다.

| 요청 예시 | 처리 |
|---|---|
| 게시글에 댓글 수를 보여준다 | Community Post + Comment. 조회용 집계만이면 진행 가능, 저장 구조 변경이면 보고 |
| 신고 승인 시 게시글을 숨긴다 | Report + Community Post. 대상 콘텐츠 상태 변경 정책이므로 먼저 보고 |
| 리뷰 신고 기능을 만든다 | Report + Review. 신고 접수만이면 진행 가능, 리뷰 자동 숨김이면 보고 |
| 축제 리뷰를 만든다 | Festival + Review. 관광지 리뷰 범위 밖이면 먼저 확인 |
| 관리자 신고 처리 API를 만든다 | Report. 권한 설정 수정이 필요하면 보고 |

## 하네스 밖으로 나가는 신호

다음 신호가 보이면 작업을 멈춥니다.

- 수정 파일이 기능별 허용 루트를 벗어난다.
- `.env`, `application*.yml`, `build.gradle`, `SecurityConfig` 수정이 필요하다.
- `auth`, `common`, 다른 도메인 패키지 수정이 필요하다.
- 기존 API 경로를 바꾸어야 한다.
- 새 테이블 관계가 다른 담당 도메인의 엔티티를 직접 참조해야 한다.
- "일단 알아서" 외에 정책 기준이 없다.

## 권장 작업 단위

하나의 에이전트 작업은 다음 중 하나로 제한하는 것을 권장합니다.

- 특정 기능의 엔티티와 Repository 추가
- 특정 기능의 Service 단위 구현
- 특정 기능의 Controller와 DTO 추가
- 특정 기능의 테스트 추가
- 특정 기능의 문서 업데이트

엔티티, API, 권한, 관리자 처리까지 한 번에 묶이면 변경 범위가 커지므로 먼저 보고합니다.
