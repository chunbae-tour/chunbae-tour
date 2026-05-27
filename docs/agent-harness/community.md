# Community Post 하네스

이 하네스는 커뮤니티 게시글 작업 전용입니다.

댓글, 신고, 리뷰, 축제 기능은 각각 별도 하네스를 사용합니다.

## 담당 기능

| 포함 | 제외 |
|---|---|
| 게시글 작성 | 댓글 작성 |
| 게시글 목록 조회 | 신고 접수 |
| 게시글 상세 조회 | 관광지 리뷰 |
| 게시글 수정 | 축제·캘린더 |
| 게시글 삭제 또는 숨김 | 관리자 공통 구조 |

## 허용 수정 범위

| 구분 | 경로 |
|---|---|
| 운영 코드 | `src/main/java/com/chunbaetour/domain/community/post/**` |
| 테스트 코드 | `src/test/java/com/chunbaetour/domain/community/post/**` |
| 도메인 문서 | `docs/park-kyunghwa-domain.md` |
| 하네스 문서 | `docs/agent-harness/community.md` |

기존 구현이 `domain/community/**` 직접 하위에 있다면, 구조 이동을 바로 하지 말고 현재 구조를 보고합니다.

## 기본 모델

```text
CommunityPost
- id
- authorId
- title
- content
- status
- viewCount
- createdAt
- updatedAt
- deletedAt
```

상태 후보는 다음입니다.

```text
ACTIVE
HIDDEN
DELETED
```

## API 후보

```text
GET    /api/v1/community/posts
POST   /api/v1/community/posts
GET    /api/v1/community/posts/{postId}
PATCH  /api/v1/community/posts/{postId}
DELETE /api/v1/community/posts/{postId}
```

## 권한 기준

| 기능 | 권한 |
|---|---|
| 목록·상세 조회 | 비로그인 가능 |
| 작성 | `USER`, `MERCHANT`, `ADMIN` |
| 수정 | 작성자 본인 또는 `ADMIN` |
| 삭제 | 작성자 본인 또는 `ADMIN` |

`SecurityConfig` 수정이 필요하면 직접 수정하지 말고 먼저 보고합니다.

## 중단하고 보고할 조건

| 상황 | 이유 |
|---|---|
| 댓글 수를 저장 컬럼으로 둘지 실시간 집계할지 애매함 | Comment와 연쇄 변경 가능 |
| 신고 승인 시 게시글 자동 숨김 필요 | Report와 연쇄 변경 |
| 게시글 카테고리 정책 미정 | API와 DB 구조 영향 |
| 이미지 첨부 필요 | 파일 저장소·업로드 정책 필요 |
| 작성자 프로필 정보를 응답에 포함해야 함 | Auth/User 조회 방식 결정 필요 |
| 공통 에러코드 추가 필요 | `common` 변경 |

## 권장 구현 순서

1. 게시글 엔티티와 상태 enum
2. Repository
3. 요청·응답 DTO
4. Service
5. Controller
6. 작성자 권한 검증
7. 단위 테스트 또는 MockMvc 통합 테스트

## 테스트 기준

| 테스트 | 기대 |
|---|---|
| 게시글 작성 성공 | 인증 사용자 ID가 작성자로 저장 |
| 목록 조회 | `ACTIVE` 게시글만 노출 |
| 상세 조회 | 존재하는 게시글 반환 |
| 작성자 외 수정 | 실패 |
| 작성자 외 삭제 | 실패 |
| 삭제 후 조회 | 목록에서 제외 |

## 완료 보고 체크

작업 완료 보고에는 다음을 포함합니다.

| 항목 | 필수 |
|---|---|
| 사용 하네스 | Community Post |
| 보호 파일 수정 여부 | 필수 |
| Comment/Report 등 타 하네스 수정 여부 | 필수 |
| 테스트 결과 | 필수 |
