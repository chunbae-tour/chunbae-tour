# Report 하네스

이 하네스는 신고 접수와 신고 처리 작업 전용입니다.

신고 대상 콘텐츠를 자동으로 숨기거나 삭제하는 정책은 다른 하네스와 연쇄 변경을 만들 수 있으므로 먼저 보고합니다.

## 담당 기능

| 포함 | 제외 |
|---|---|
| 신고 접수 | 게시글 본문 수정 |
| 신고 사유 관리 | 댓글 본문 수정 |
| 내 신고 내역 조회 | 리뷰 본문 수정 |
| 관리자 신고 목록 조회 | 관리자 공통 권한 구조 변경 |
| 관리자 신고 상태 변경 | 자동 제재·자동 숨김 정책 |

## 허용 수정 범위

| 구분 | 경로 |
|---|---|
| 운영 코드 | `src/main/java/com/chunbaetour/domain/report/**` |
| 테스트 코드 | `src/test/java/com/chunbaetour/domain/report/**` |
| 도메인 문서 | `docs/park-kyunghwa-domain.md` |
| 하네스 문서 | `docs/agent-harness/report.md` |

관리자 권한 설정을 위해 `SecurityConfig` 수정이 필요하면 직접 수정하지 말고 먼저 보고합니다.

## 기본 모델

```text
Report
- id
- reporterId
- targetType
- targetId
- reason
- description
- status
- adminMemo
- handledBy
- handledAt
- createdAt
- updatedAt
```

신고 대상 타입 후보는 다음입니다.

```text
POST
COMMENT
TOURIST_SPOT_REVIEW
USER
```

신고 상태 후보는 다음입니다.

```text
PENDING
ACCEPTED
REJECTED
CANCELED
```

신고 사유 후보는 다음입니다.

```text
SPAM
ABUSE
HATE
SEXUAL
SCAM
WRONG_INFORMATION
OTHER
```

## API 후보

```text
POST  /api/v1/reports
GET   /api/v1/users/me/reports

GET   /api/v1/admin/reports
GET   /api/v1/admin/reports/{reportId}
PATCH /api/v1/admin/reports/{reportId}
```

## 권한 기준

| 기능 | 권한 |
|---|---|
| 신고 접수 | `USER`, `MERCHANT`, `ADMIN` |
| 내 신고 내역 조회 | 본인 |
| 신고 목록 조회 | `ADMIN` |
| 신고 처리 | `ADMIN` |

## 중단하고 보고할 조건

| 상황 | 이유 |
|---|---|
| 신고 대상 존재 여부를 실제 엔티티로 검증해야 함 | Community/Review 등 타 도메인 접근 필요 |
| 신고 승인 시 대상 자동 숨김 필요 | 타 도메인 상태 변경 필요 |
| 동일 대상 중복 신고 정책이 애매함 | DB unique 조건 영향 |
| 신고 누적 시 자동 제재 필요 | User/Auth와 연쇄 변경 |
| 관리자 권한 설정 수정 필요 | SecurityConfig 영향 |
| 공통 에러코드 추가 필요 | `common` 변경 |

## 권장 구현 순서

1. 신고 대상 타입, 사유, 상태 enum
2. 신고 엔티티
3. 중복 신고 기준 결정
4. Repository
5. 요청·응답 DTO
6. 신고 접수 Service
7. 관리자 처리 Service
8. Controller
9. 테스트

## 테스트 기준

| 테스트 | 기대 |
|---|---|
| 신고 접수 성공 | `PENDING` 상태로 저장 |
| 중복 신고 | 정책에 따라 실패 또는 기존 신고 반환 |
| 내 신고 내역 | 본인 신고만 조회 |
| 관리자 처리 | 상태와 처리자, 처리 시간이 저장 |
| 처리 완료 신고 재처리 | 정책에 따라 실패 |

## 완료 보고 체크

| 항목 | 필수 |
|---|---|
| 사용 하네스 | Report |
| 자동 숨김 등 타 도메인 영향 여부 | 필수 |
| 보호 파일 수정 여부 | 필수 |
| 테스트 결과 | 필수 |
