# 박경화 담당 도메인 정리

이 문서는 박경화 담당 범위를 다른 도메인과 분리해서 보기 위한 작업 기준 문서입니다.

현재 제공된 프로젝트 개요와 사용자 지시를 기준으로, 문서에 `박경화`라고 직접 표시되지 않았더라도 다음 기능은 박경화 담당 도메인으로 봅니다.

- 커뮤니티 게시판
- 댓글
- 신고
- 축제·행사 캘린더
- 관광지 리뷰와 별점

작업 에이전트는 반드시 [에이전트 최초 진입점](./agent-entrypoint.md)에서 시작하고, 아래 기능별 하네스 중 필요한 문서만 읽고 진행합니다.

| 기능 | 하네스 |
|---|---|
| 커뮤니티 게시글 | [Community Post 하네스](./agent-harness/community.md) |
| 댓글 | [Comment 하네스](./agent-harness/comment.md) |
| 신고 | [Report 하네스](./agent-harness/report.md) |
| 축제·캘린더 | [Festival 하네스](./agent-harness/festival.md) |
| 관광지 리뷰 | [Review 하네스](./agent-harness/review.md) |

## 담당 범위 요약

| 담당 도메인 | MVP 기능 | 핵심 책임 |
|---|---|---|
| Community | 게시글 CRUD, 게시글 목록·상세 조회 | 여행 정보 공유와 사용자 커뮤니티 |
| Comment | 게시글 댓글 CRUD | 게시글 단위 소통 |
| Report | 게시글·댓글·리뷰 신고 | 부적절한 콘텐츠를 관리자 처리 대상으로 전달 |
| Festival | 축제·행사 조회, 캘린더 | 날짜 기반 행사 탐색 |
| Review | 관광지 리뷰 작성·조회·수정·삭제, 별점 | 관광지 경험 평가 |

## 담당하지 않는 범위

다음은 박경화 도메인과 연동될 수 있지만 직접 소유 범위는 아닙니다.

| 도메인 | 관계 |
|---|---|
| Auth/User | 작성자 식별과 권한 확인에 사용 |
| Tourism Location | 관광지 기본 정보와 위치 탐색 원천 데이터 |
| Chat | 커뮤니티와 별개 실시간 소통 채널 |
| Payment | 엽전 충전·소비·결제 |
| Merchant | 상인 등록, 가게, 메뉴 관리 |
| Admin | 신고 처리 화면과 관리자 권한 API |
| Search | 인기 검색어 집계 |
| AI/Support | FAQ AI 답변, 고객센터 상담 |

## 도메인 경계

박경화 도메인은 사용자 생성 콘텐츠와 날짜 기반 관광 콘텐츠를 중심으로 합니다.

직접 소유하는 데이터는 다음입니다.

- 게시글
- 댓글
- 신고 접수 기록
- 관광지 리뷰
- 축제·행사 조회 모델 또는 축제 데이터 관리 모델

다른 도메인의 데이터는 직접 수정하지 않고 참조합니다.

- 작성자는 `accountId`로 참조합니다.
- 관광지는 `touristSpotId`로 참조합니다.
- 신고 대상은 대상 타입과 대상 ID로 참조합니다.
- 관리자 처리는 `Report` 상태 변경 API로 연결합니다.

## 권한 기준

| 기능 | 비로그인 | USER | MERCHANT | ADMIN |
|---|---:|---:|---:|---:|
| 게시글 목록·상세 조회 | 가능 | 가능 | 가능 | 가능 |
| 게시글 작성·수정·삭제 | 불가 | 가능 | 가능 | 가능 |
| 댓글 목록 조회 | 가능 | 가능 | 가능 | 가능 |
| 댓글 작성·수정·삭제 | 불가 | 가능 | 가능 | 가능 |
| 신고 접수 | 불가 | 가능 | 가능 | 가능 |
| 내 신고 내역 조회 | 불가 | 가능 | 가능 | 가능 |
| 신고 처리 | 불가 | 불가 | 불가 | 가능 |
| 축제·행사 조회 | 가능 | 가능 | 가능 | 가능 |
| 축제·행사 관리 | 불가 | 불가 | 불가 | 가능 |
| 관광지 리뷰 조회 | 가능 | 가능 | 가능 | 가능 |
| 관광지 리뷰 작성·수정·삭제 | 불가 | 가능 | 가능 | 가능 |

삭제 정책은 실제 물리 삭제보다 상태 변경 방식의 소프트 삭제를 우선 검토합니다.

## Community 도메인

커뮤니티는 여행 정보 공유와 사용자 간 소통을 담당합니다.

### 주요 기능

- 게시글 작성
- 게시글 목록 조회
- 게시글 상세 조회
- 게시글 수정
- 게시글 삭제
- 작성자 본인 여부 확인
- 관리자 삭제 또는 숨김 처리 확장

### 엔티티 후보

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

`status` 후보는 다음과 같습니다.

```text
ACTIVE
HIDDEN
DELETED
```

### API 후보

```text
GET    /api/v1/community/posts
POST   /api/v1/community/posts
GET    /api/v1/community/posts/{postId}
PATCH  /api/v1/community/posts/{postId}
DELETE /api/v1/community/posts/{postId}
```

## Comment 도메인

댓글은 커뮤니티 게시글에 종속됩니다.

### 주요 기능

- 게시글별 댓글 목록 조회
- 댓글 작성
- 댓글 수정
- 댓글 삭제
- 작성자 본인 여부 확인
- 신고 대상 연결

### 엔티티 후보

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

### API 후보

```text
GET    /api/v1/community/posts/{postId}/comments
POST   /api/v1/community/posts/{postId}/comments
PATCH  /api/v1/community/posts/{postId}/comments/{commentId}
DELETE /api/v1/community/posts/{postId}/comments/{commentId}
```

## Report 도메인

신고는 부적절한 게시글, 댓글, 리뷰, 사용자 행위를 관리자 처리 대상으로 접수하는 기능입니다.

### 주요 기능

- 게시글 신고
- 댓글 신고
- 관광지 리뷰 신고
- 신고 사유 입력
- 중복 신고 방지 정책
- 내 신고 내역 조회
- 관리자 신고 목록 조회
- 관리자 신고 처리

### 신고 대상 타입 후보

```text
POST
COMMENT
TOURIST_SPOT_REVIEW
USER
```

### 신고 상태 후보

```text
PENDING
ACCEPTED
REJECTED
CANCELED
```

### 신고 사유 후보

```text
SPAM
ABUSE
HATE
SEXUAL
SCAM
WRONG_INFORMATION
OTHER
```

### 엔티티 후보

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

### API 후보

```text
POST  /api/v1/reports
GET   /api/v1/users/me/reports

GET   /api/v1/admin/reports
GET   /api/v1/admin/reports/{reportId}
PATCH /api/v1/admin/reports/{reportId}
```

## Festival 도메인

축제·행사는 관광지 탐색과 연결되는 날짜 기반 콘텐츠입니다.

### 주요 기능

- 축제 전체 조회
- 축제 상세 조회
- 날짜 범위 조회
- 월별 캘린더 조회
- 지역 기준 필터링
- 관리자 축제 등록·수정·삭제 확장

### 엔티티 후보

```text
Festival
- id
- name
- description
- areaName
- address
- startDate
- endDate
- eventTime
- imageUrl
- sourceUrl
- status
- createdAt
- updatedAt
```

`status` 후보는 다음과 같습니다.

```text
SCHEDULED
ONGOING
ENDED
HIDDEN
```

### API 후보

```text
GET /api/v1/festivals
GET /api/v1/festivals/{festivalId}
GET /api/v1/festivals/calendar?year=2026&month=5
GET /api/v1/festivals/search?areaName=서울&from=2026-05-01&to=2026-05-31
```

관리자 API 후보는 다음과 같습니다.

```text
POST   /api/v1/admin/festivals
PATCH  /api/v1/admin/festivals/{festivalId}
DELETE /api/v1/admin/festivals/{festivalId}
```

## Review 도메인

리뷰는 관광지 경험을 별점과 텍스트로 기록하는 사용자 생성 콘텐츠입니다.

### 주요 기능

- 관광지별 리뷰 목록 조회
- 내 리뷰 조회
- 리뷰 작성
- 리뷰 수정
- 리뷰 삭제
- 별점 평균 계산
- 신고 대상 연결

### 엔티티 후보

```text
TouristSpotReview
- id
- touristSpotId
- authorId
- rating
- content
- status
- createdAt
- updatedAt
- deletedAt
```

별점은 1점부터 5점까지의 정수 또는 0.5 단위 소수 정책 중 하나로 고정해야 합니다. MVP에서는 구현과 검증이 단순한 1~5 정수 별점을 우선 권장합니다.

### API 후보

```text
GET    /api/v1/tourist-spots/{touristSpotId}/reviews
POST   /api/v1/tourist-spots/{touristSpotId}/reviews
PATCH  /api/v1/tourist-spots/{touristSpotId}/reviews/{reviewId}
DELETE /api/v1/tourist-spots/{touristSpotId}/reviews/{reviewId}
GET    /api/v1/users/me/reviews
```

## 패키지 구조 제안

현재 프로젝트는 `com.chunbaetour.domain` 아래에 도메인별 패키지를 두는 구조입니다.

박경화 담당 도메인은 다음 중 하나의 방식을 선택할 수 있습니다.

### 방식 A. 기능별 패키지 분리

```text
com.chunbaetour.domain.community
com.chunbaetour.domain.report
com.chunbaetour.domain.festival
com.chunbaetour.domain.review
```

장점은 각 기능의 경계가 명확하다는 점입니다. MVP에서 여러 사람이 병렬 작업한다면 이 방식을 우선 권장합니다.

### 방식 B. 콘텐츠 패키지로 묶기

```text
com.chunbaetour.domain.content
├── community
├── report
├── festival
└── review
```

장점은 사용자 생성 콘텐츠를 한 영역으로 볼 수 있다는 점입니다. 다만 현재 저장소의 `auth`, `common` 구조와는 방식 A가 더 잘 맞습니다.

## 구현 우선순위 제안

박경화 담당 범위 안에서는 다음 순서가 MVP 완성에 유리합니다.

1. 관광지 리뷰 기본 CRUD와 별점 검증
2. 커뮤니티 게시글 CRUD
3. 댓글 CRUD
4. 신고 접수와 신고 대상 타입 모델링
5. 관리자 신고 목록·처리 API
6. 축제 전체 조회와 상세 조회
7. 월별 축제 캘린더 조회

이 순서는 다른 도메인 의존도가 낮은 기능부터 쌓는 흐름입니다. 축제 데이터 원천이나 관광지 기본 엔티티가 아직 없으면, 리뷰와 축제는 `touristSpotId` 같은 참조 ID 중심으로 먼저 열어둘 수 있습니다.

## 테스트 기준

각 도메인은 최소 다음 테스트를 둡니다.

| 도메인 | 필수 테스트 |
|---|---|
| Community | 작성 성공, 작성자 외 수정 실패, 삭제 후 조회 제외 |
| Comment | 댓글 작성 성공, 다른 게시글 댓글 수정 방지, 작성자 외 삭제 실패 |
| Report | 신고 접수 성공, 중복 신고 방지, 관리자 처리 상태 변경 |
| Festival | 날짜 범위 조회, 월별 캘린더 조회, 숨김 상태 제외 |
| Review | 별점 범위 검증, 중복 리뷰 정책, 작성자 외 수정 실패 |

통합 테스트는 기존 `auth` 테스트처럼 `@SpringBootTest`와 `MockMvc` 흐름을 우선 참고합니다.

## 공통 에러코드 후보

현재 `ErrorCode`에 없는 도메인 에러는 추가가 필요합니다.

후보는 다음과 같습니다.

```text
COMMUNITY_001 게시글을 찾을 수 없음
COMMUNITY_002 게시글 수정 권한 없음
COMMUNITY_003 게시글 삭제 권한 없음

COMMENT_001 댓글을 찾을 수 없음
COMMENT_002 댓글 수정 권한 없음
COMMENT_003 댓글 삭제 권한 없음

REPORT_001 신고 대상을 찾을 수 없음
REPORT_002 이미 신고한 대상
REPORT_003 신고를 찾을 수 없음
REPORT_004 신고 처리 권한 없음

FESTIVAL_001 축제를 찾을 수 없음
FESTIVAL_002 잘못된 날짜 범위

REVIEW_001 리뷰를 찾을 수 없음
REVIEW_002 별점 범위 오류
REVIEW_003 이미 리뷰를 작성한 관광지
REVIEW_004 리뷰 수정 권한 없음
REVIEW_005 리뷰 삭제 권한 없음
```

실제 코드 추가 시에는 기존 `ErrorCode` 네이밍과 HTTP status 매핑을 먼저 확인하고 맞춥니다.

## 다른 도메인과의 연결점

| 연결 대상 | 연결 방식 |
|---|---|
| Auth/User | JWT 인증 사용자 ID를 작성자·신고자로 사용 |
| Tourism | `touristSpotId`로 리뷰와 축제 주변 정보 연결 |
| Admin | 신고 처리, 축제 관리, 콘텐츠 숨김 처리 |
| Notification | 신고 처리 결과나 댓글 알림은 추후 확장 가능 |
| Search | 게시글·축제 검색어가 인기 검색어로 집계될 수 있음 |

## 결정이 필요한 정책

구현 전에 팀에서 확정하면 좋은 정책입니다.

- 게시글 카테고리를 둘지 여부
- 게시글 이미지 첨부를 MVP에 포함할지 여부
- 댓글 대댓글을 허용할지 여부
- 리뷰는 관광지당 사용자 1개만 허용할지 여부
- 별점은 정수만 받을지 0.5 단위를 허용할지 여부
- 신고 중복 기준을 `reporterId + targetType + targetId`로 둘지 여부
- 신고가 승인되면 대상 콘텐츠를 자동 숨김 처리할지 여부
- 축제 데이터는 관리자가 직접 입력할지 외부 API를 수집할지 여부
