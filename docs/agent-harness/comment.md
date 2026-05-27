# Comment 하네스

이 하네스는 커뮤니티 게시글 댓글 작업 전용입니다.

게시글 본문 기능은 Community Post 하네스를 사용합니다.

## 담당 기능

| 포함 | 제외 |
|---|---|
| 게시글별 댓글 목록 조회 | 게시글 본문 CRUD |
| 댓글 작성 | 신고 접수 |
| 댓글 수정 | 관광지 리뷰 |
| 댓글 삭제 또는 숨김 | 채팅 메시지 |
| 작성자 권한 확인 | 관리자 공통 구조 |

## 허용 수정 범위

| 구분 | 경로 |
|---|---|
| 운영 코드 | `src/main/java/com/chunbaetour/domain/community/comment/**` |
| 테스트 코드 | `src/test/java/com/chunbaetour/domain/community/comment/**` |
| 도메인 문서 | `docs/park-kyunghwa-domain.md` |
| 하네스 문서 | `docs/agent-harness/comment.md` |

게시글 존재 확인이 필요해 Community Post 쪽 수정이 필요하면 먼저 보고합니다.

## 기본 모델

```text
Comment
- id
- postId
- authorId
- content
- status
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
GET    /api/v1/community/posts/{postId}/comments
POST   /api/v1/community/posts/{postId}/comments
PATCH  /api/v1/community/posts/{postId}/comments/{commentId}
DELETE /api/v1/community/posts/{postId}/comments/{commentId}
```

## 권한 기준

| 기능 | 권한 |
|---|---|
| 댓글 목록 조회 | 비로그인 가능 |
| 댓글 작성 | `USER`, `MERCHANT`, `ADMIN` |
| 댓글 수정 | 작성자 본인 또는 `ADMIN` |
| 댓글 삭제 | 작성자 본인 또는 `ADMIN` |

`SecurityConfig` 수정이 필요하면 직접 수정하지 말고 먼저 보고합니다.

## 중단하고 보고할 조건

| 상황 | 이유 |
|---|---|
| 대댓글이 필요함 | 모델 구조가 달라짐 |
| 댓글 수를 게시글에 저장해야 함 | Community Post와 연쇄 변경 |
| 신고 승인 시 댓글 자동 숨김 필요 | Report와 연쇄 변경 |
| 게시글 삭제 시 댓글 처리 정책 미정 | 삭제 정책 결정 필요 |
| 작성자 닉네임을 응답에 포함해야 함 | Auth/User 조회 방식 결정 필요 |
| 공통 에러코드 추가 필요 | `common` 변경 |

## 권장 구현 순서

1. 댓글 엔티티와 상태 enum
2. Repository
3. 요청·응답 DTO
4. Service
5. Controller
6. 작성자 권한 검증
7. 게시글 ID 일치 검증
8. 테스트

## 테스트 기준

| 테스트 | 기대 |
|---|---|
| 댓글 작성 성공 | 게시글 ID와 작성자 ID가 저장 |
| 게시글별 목록 조회 | 해당 게시글 댓글만 반환 |
| 다른 게시글 댓글 수정 | 실패 |
| 작성자 외 수정 | 실패 |
| 작성자 외 삭제 | 실패 |
| 삭제 후 조회 | 목록에서 제외 |

## 완료 보고 체크

| 항목 | 필수 |
|---|---|
| 사용 하네스 | Comment |
| Community Post 수정 여부 | 필수 |
| 보호 파일 수정 여부 | 필수 |
| 테스트 결과 | 필수 |
