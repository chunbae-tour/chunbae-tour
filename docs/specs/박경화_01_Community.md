# 춘배투어 - 박경화 파트 도메인 명세서

> **용도**: Claude CLI가 읽고 구현 작업에 사용하기 위한 참조 문서  
> **담당 도메인**: 커뮤니티(게시글·댓글) / 관광지 리뷰(Place 도메인 추가 구현) / 캘린더(축제) / 신고  
> **프로젝트**: 춘배투어 (ChunBae Tour)  
> **기술 스택**: Java 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL(RDS), AWS S3  
> **Base URL**: `/api/v1`

---


---

## 1. Community 도메인 (게시글 · 댓글)

### 1-1. 패키지 구조

```
domain/community
├── controller
│   ├── PostController.java
│   └── CommentController.java
├── service
│   ├── PostService.java
│   └── CommentService.java
├── repository
│   ├── PostRepository.java
│   ├── PostQueryRepository.java        ← QueryDSL
│   └── CommentRepository.java
├── entity
│   ├── Post.java
│   └── Comment.java
├── dto
│   ├── request
│   │   ├── CompanionPostCreateRequest.java
│   │   ├── CompanionPostUpdateRequest.java
│   │   ├── FreePostCreateRequest.java
│   │   ├── FreePostUpdateRequest.java
│   │   ├── CommentCreateRequest.java
│   │   └── CommentUpdateRequest.java
│   └── response
│       ├── PostResponse.java
│       └── CommentResponse.java
└── type
    ├── PostType.java          (COMPANION, FREE)
    ├── PostStatus.java        (ACTIVE, BLOCKED, DELETED, CLOSED)
    ├── PostSortType.java      (LATEST, POPULAR)
    └── CommentStatus.java      (ACTIVE, DELETED)
```

### 1-2. 동행 게시판 API

> 게시글 status: `ACTIVE / BLOCKED / DELETED`  
> BLOCKED = 신고 처리로 숨김 상태  
> 모집 진행 여부는 현재 인원/최대 인원 및 연결된 채팅방 상태로 판단 (별도 모집 상태값 없음)  
> 삭제는 Soft Delete (`deletedAt` 설정)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/posts/companions` | 동행 게시글 생성 | ✅ USER |
| GET | `/posts/companions` | 동행 게시글 목록 (`region?`, `meetingDate?`, `sort?`, `cursor`, `size`) | ✅ USER |
| GET | `/posts/companions/{id}` | 동행 게시글 상세 | ✅ USER |
| PUT | `/posts/companions/{id}` | 동행 게시글 수정 | ✅ USER (본인) |
| DELETE | `/posts/companions/{id}` | 동행 게시글 삭제 | ✅ USER (본인) - Soft Delete |

> **조회수 처리**: `GET /posts/companions/{id}` (게시글 상세 조회) 호출 시 `Redis INCR "post:view:{postId}"` 실행.  
> 배치 스케줄러(5분 주기)로 Redis 카운트를 DB에 벌크 동기화한다. → Section 10 참조

**POST `/posts/companions`**

Request:
```json
{
  "title": "7/20 경복궁 같이 가실 분!",
  "content": "오전 10시에 경복궁 앞에서 만나서 같이 둘러볼 분 구합니다.",
  "placeId": 42,
  "placeNameOverride": null,
  "meetingDate": "2026-07-20",
  "maxMembers": 4,
  "region": "서울"
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 301,
    "title": "7/20 경복궁 같이 가실 분!",
    "content": "오전 10시에 경복궁 앞에서 만나서 같이 둘러볼 분 구합니다.",
    "placeId": 42,
    "placeName": "경복궁",
    "meetingDate": "2026-07-20",
    "maxMembers": 4,
    "currentMembers": 1,
    "region": "서울",
    "status": "ACTIVE",
    "chatRoomId": null,
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",
      "companionScore": 4.3
    },
    "createdAt": "2026-07-15T10:00:00Z"
  }
}
```

**PUT `/posts/companions/{id}`**

> `region`은 수정 시에도 변경 가능하다.

Request:
```json
{
  "title": "7/20 경복궁 같이 가실 분! (수정)",
  "content": "오전 10시 → 오전 11시로 변경합니다.",
  "placeId": 42,
  "meetingDate": "2026-07-20",
  "maxMembers": 5,
  "region": "서울"
}
```

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 301,
    "title": "7/20 경복궁 같이 가실 분! (수정)",
    "content": "오전 10시 → 오전 11시로 변경합니다.",
    "placeId": 42,
    "placeName": "경복궁",
    "meetingDate": "2026-07-20",
    "maxMembers": 5,
    "currentMembers": 1,
    "region": "서울",
    "status": "ACTIVE",
    "chatRoomId": null,
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",
      "companionScore": 4.3
    },
    "createdAt": "2026-07-15T10:00:00Z",
    "updatedAt": "2026-07-15T12:00:00Z"
  }
}
```

**GET `/posts/companions?region=서울&meetingDate=2026-07-20&sort=LATEST&cursor=eyJpZCI6MzAwfQ==&size=10`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "postId": 299,
        "title": "제주도 올레길 같이 걸어요",
        "placeName": "올레길 7코스",
        "meetingDate": "2026-07-25",
        "maxMembers": 3,
        "currentMembers": 2,
        "region": "제주",
        "status": "ACTIVE",
        "writer": {
          "userId": 1005,
          "nickname": "제주사랑",
          "profileImageUrl": "https://cdn.example.com/profiles/1005.jpg",
          "companionScore": 4.8
        },
        "chatRoomId": 55,
        "viewCount": 32,
        "likeCount": 3,
        "commentCount": 5,
        "createdAt": "2026-07-14T18:00:00Z",
        "updatedAt": null
      }
    ],
    "nextCursor": "eyJpZCI6Mjk5fQ==",
    "hasNext": true,
    "size": 10
  }
}
```

**GET `/posts/companions/{id}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 301,
    "title": "7/20 경복궁 같이 가실 분!",
    "content": "오전 10시에 경복궁 앞에서 만나서 같이 둘러볼 분 구합니다.",
    "placeId": 42,
    "placeName": "경복궁",
    "meetingDate": "2026-07-20",
    "maxMembers": 4,
    "currentMembers": 2,
    "region": "서울",
    "status": "ACTIVE",
    "chatRoomId": 55,
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",
      "companionScore": 4.3
    },
    "viewCount": 32,
    "likeCount": 3,
    "commentCount": 5,
    "createdAt": "2026-07-15T10:00:00Z"
  }
}
```

**DELETE `/posts/companions/{id}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "게시글이 삭제되었습니다."
}
```

### 1-3. 자유 게시판 API

> 삭제는 Soft Delete (`deletedAt` 설정)
> **`sort` 옵션**: `LATEST` (최신순, 기본값) / `POPULAR` (인기순 — 좋아요 수 기준, 추후 좋아요 기능 연동 시 활성화)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/posts/free` | 자유 게시글 생성 | ✅ USER |
| GET | `/posts/free` | 자유 게시글 목록 (`cursor`, `size`, `sort`) | ❌ |
| GET | `/posts/free/{id}` | 자유 게시글 상세 | ❌ |
| PUT | `/posts/free/{id}` | 자유 게시글 수정 | ✅ USER (본인) |
| DELETE | `/posts/free/{id}` | 자유 게시글 삭제 | ✅ USER (본인) - Soft Delete |

**POST `/posts/free`**

Request:
```json
{
  "title": "전주 한옥마을 여행 팁 공유",
  "content": "주말에는 사람이 많아서 오전에 방문하는 걸 추천합니다.",
  "imageUrls": ["https://cdn.example.com/uploads/post_img1.jpg"]
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 1020,
    "title": "전주 한옥마을 여행 팁 공유",
    "content": "주말에는 사람이 많아서 오전에 방문하는 걸 추천합니다.",
    "imageUrls": ["https://cdn.example.com/uploads/post_img1.jpg"],
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg"
    },
    "viewCount": 0,
    "likeCount": 0,
    "createdAt": "2026-07-15T21:00:00Z"
  }
}
```

**PUT `/posts/free/{id}`**

Request:
```json
{
  "title": "전주 한옥마을 여행 팁 공유 (업데이트)",
  "content": "오전 9시 이전 방문 추천! 한복 대여는 미리 예약하세요.",
  "imageUrls": ["https://cdn.example.com/uploads/post_img1.jpg", "https://cdn.example.com/uploads/post_img2.jpg"]
}
```

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 1020,
    "title": "전주 한옥마을 여행 팁 공유 (업데이트)",
    "content": "오전 9시 이전 방문 추천! 한복 대여는 미리 예약하세요.",
    "imageUrls": [
      "https://cdn.example.com/uploads/post_img1.jpg",
      "https://cdn.example.com/uploads/post_img2.jpg"
    ],
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg"
    },
    "updatedAt": "2026-07-16T09:00:00Z"
  }
}
```

**GET `/posts/free?cursor=eyJpZCI6MTAyMH0=&size=10&sort=LATEST`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "postId": 1019,
        "title": "경복궁 야간 개장 후기",
        "writer": {
          "userId": 1005,
          "nickname": "제주사랑",
          "profileImageUrl": "https://cdn.example.com/profiles/1005.jpg"
        },
        "viewCount": 45,
        "likeCount": 7,
        "commentCount": 3,
        "createdAt": "2026-07-14T20:00:00Z",
        "updatedAt": null
      }
    ],
    "nextCursor": "eyJpZCI6MTAxOX0=",
    "hasNext": true,
    "size": 10
  }
}
```

**GET `/posts/free/{id}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": 1020,
    "title": "전주 한옥마을 여행 팁 공유",
    "content": "주말에는 사람이 많아서 오전에 방문하는 걸 추천합니다.",
    "imageUrls": ["https://cdn.example.com/uploads/post_img1.jpg"],
    "writer": {
      "userId": 1001,
      "nickname": "한국여행자",
      "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg"
    },
    "viewCount": 45,
    "likeCount": 7,
    "commentCount": 3,
    "createdAt": "2026-07-15T21:00:00Z",
    "updatedAt": null
  }
}
```

**DELETE `/posts/free/{id}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "게시글이 삭제되었습니다."
}
```

> **조회수 처리**: `GET /posts/free/{id}` (게시글 상세 조회) 호출 시 `Redis INCR "post:view:{postId}"` 실행.  
> 배치 스케줄러(5분 주기)로 Redis 카운트를 DB에 벌크 동기화한다. → Section 10 참조

### 1-4. 댓글 API

> **동행 게시판과 자유 게시판 모두에 적용된다.** `postId`로 어느 게시판의 댓글인지 구분한다.  
> 대댓글 지원: `parentCommentId`가 null이면 일반 댓글, 있으면 대댓글  
> 삭제는 Soft Delete (`deletedAt` 설정)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/comments` | 댓글 또는 대댓글 생성 (`parentCommentId` null이면 댓글, 값 있으면 대댓글) | ✅ USER |
| GET | `/comments` | 댓글 목록 - `postId` 필수, 대댓글 포함 (`cursor`, `size`) | ❌ |
| PATCH | `/comments/{id}` | 댓글 수정 | ✅ USER (본인) |
| DELETE | `/comments/{id}` | 댓글 삭제 | ✅ USER (본인) - Soft Delete |

**POST `/comments`**

Request:
```json
{
  "postId": 301,
  "content": "저도 참여하고 싶어요! 한복 체험 좋아합니다.",
  "parentCommentId": null
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "commentId": 801,
    "postId": 301,
    "content": "저도 참여하고 싶어요! 한복 체험 좋아합니다.",
    "parentCommentId": null,
    "writer": {
      "userId": 1003,
      "nickname": "여행초보",
      "profileImageUrl": "https://cdn.example.com/profiles/1003.jpg"
    },
    "createdAt": "2026-07-15T11:00:00Z"
  }
}
```

**GET `/comments?postId=301&cursor=eyJpZCI6ODAyfQ==&size=20`**

Response 200 (대댓글 포함):
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "commentId": 801,
        "postId": 301,
        "content": "저도 참여하고 싶어요!",
        "parentCommentId": null,
        "writer": {
          "userId": 1003,
          "nickname": "여행초보",
          "profileImageUrl": "https://cdn.example.com/profiles/1003.jpg"
        },
        "replies": [
          {
            "commentId": 803,
            "content": "환영합니다! 채팅방으로 오세요 :)",
            "parentCommentId": 801,
            "writer": {
              "userId": 1001,
              "nickname": "한국여행자",
              "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg"
            },
            "createdAt": "2026-07-15T11:10:00Z"
          }
        ],
        "createdAt": "2026-07-15T11:00:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6ODAx",
    "hasNext": false,
    "size": 20
  }
}
```

**POST `/comments` — 대댓글 생성 예시**

Request:
```json
{
  "postId": 301,
  "content": "저도 함께하고 싶어요!",
  "parentCommentId": 801
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "commentId": 810,
    "postId": 301,
    "content": "저도 함께하고 싶어요!",
    "parentCommentId": 801,
    "writer": {
      "userId": 1007,
      "nickname": "서울나들이",
      "profileImageUrl": "https://cdn.example.com/profiles/1007.jpg"
    },
    "createdAt": "2026-07-15T11:30:00Z"
  }
}
```

**PATCH `/comments/{id}`**

Request:
```json
{
  "content": "수정된 댓글 내용입니다."
}
```

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "commentId": 801,
    "content": "수정된 댓글 내용입니다.",
    "updatedAt": "2026-07-15T12:00:00Z"
  }
}
```

**DELETE `/comments/{id}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "댓글이 삭제되었습니다."
}
```

### 1-5. 관광지 리뷰 API

> **관광지 리뷰는 Place 도메인(Section 2)에서 구현한다.**  
> API 명세, 패키지 구조, 에러 코드, 파일 업로드 정책은 **Section 2**를 참조한다.

### 1-6. Community 에러 코드

| 에러코드 | HTTP | 메시지 | 발생 상황 |
|---------|------|--------|----------|
| `COMMUNITY_001` | 404 | 존재하지 않는 게시글입니다. | 게시글 ID 조회 시 없을 때 |
| `COMMUNITY_002` | 404 | 존재하지 않는 댓글입니다. | 댓글 ID 조회 시 없을 때 |
| `COMMUNITY_003` | 403 | 해당 게시글을 수정할 권한이 없습니다. | 타인 게시글 수정 시도 |
| `COMMUNITY_004` | 403 | 해당 게시글을 삭제할 권한이 없습니다. | 타인 게시글 삭제 시도 |
| `COMMUNITY_005` | 403 | 해당 댓글을 수정할 권한이 없습니다. | 타인 댓글 수정 시도 |
| `COMMUNITY_006` | 403 | 해당 댓글을 삭제할 권한이 없습니다. | 타인 댓글 삭제 시도 |
| `COMMUNITY_007` | 400 | 게시글 제목은 최대 100자까지 입력 가능합니다. | 제목 길이 초과 |
| `COMMUNITY_008` | 400 | 게시글 내용은 최대 2000자까지 입력 가능합니다. | 내용 길이 초과 |
| `COMMUNITY_013` | 400 | 이미지는 최대 5장까지 첨부 가능합니다. | 이미지 수 초과 |
| `COMMUNITY_014` | 400 | 이미지 형식이 올바르지 않습니다. (jpg, png, webp) | 허용되지 않는 이미지 형식 |

> `COMMUNITY_009`는 신고 에러코드이나 Report 도메인으로 분리됨 → `REPORT_001`로 대체됨. Section 4-4 참조  
> `COMMUNITY_010`, `COMMUNITY_011`, `COMMUNITY_012`, `COMMUNITY_015`는 리뷰 에러코드였으나 리뷰가 Place 도메인으로 이동하면서 삭제됨. 의도적 불연속이며 `PLACE_001~006`으로 대체됨.  
> `COMMUNITY_013`, `COMMUNITY_014`는 게시글·리뷰 이미지 공통으로 재사용한다. 리뷰 이미지 에러는 Place 도메인에서 이 코드를 그대로 사용한다.

**ErrorCode Enum (Community 부분)**:
```java
POST_NOT_FOUND(HttpStatus.NOT_FOUND,           "COMMUNITY_001", "존재하지 않는 게시글입니다."),
COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND,        "COMMUNITY_002", "존재하지 않는 댓글입니다."),
POST_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN,    "COMMUNITY_003", "해당 게시글을 수정할 권한이 없습니다."),
POST_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN,    "COMMUNITY_004", "해당 게시글을 삭제할 권한이 없습니다."),
COMMENT_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMUNITY_005", "해당 댓글을 수정할 권한이 없습니다."),
COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMUNITY_006", "해당 댓글을 삭제할 권한이 없습니다."),
POST_TITLE_TOO_LONG(HttpStatus.BAD_REQUEST,    "COMMUNITY_007", "게시글 제목은 최대 100자까지 입력 가능합니다."),
POST_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST,  "COMMUNITY_008", "게시글 내용은 최대 2000자까지 입력 가능합니다."),
IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST,   "COMMUNITY_013", "이미지는 최대 5장까지 첨부 가능합니다."),
INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST,   "COMMUNITY_014", "이미지 형식이 올바르지 않습니다. (jpg, png, webp)"),
```

### 1-7. 파일 업로드 정책 (S3)

| 항목 | 제한 |
|------|------|
| 허용 확장자 | jpg, jpeg, png, webp |
| 게시글 이미지 | 최대 5장, 장당 10MB |
| 파일명 | UUID 기반으로 변경 저장 |
| 저장소 | AWS S3 (`global.infra.s3.S3Uploader` 사용) |

### 1-8. 화면 연결 (화면 설계서 참조)

| 화면 ID | 화면명 | 관련 API |
|--------|--------|---------|
| SCR-COM-001 | 커뮤니티 목록 | `GET /posts/companions`, `GET /posts/free` |
| SCR-COM-002 | 게시글 상세 | `GET /posts/companions/{id}` 또는 `GET /posts/free/{id}`, `GET /comments`, `POST /comments`, `PATCH /comments/{id}`, `DELETE /comments/{id}`, `PUT /posts/companions/{id}` 또는 `PUT /posts/free/{id}`, `DELETE /posts/companions/{id}` 또는 `DELETE /posts/free/{id}` |
| SCR-COM-003 | 게시글 작성 | `POST /posts/companions`, `POST /posts/free` |

**SCR-COM-001 커뮤니티 목록 구성**:
- 탭: [동행 모집] / [자유 게시판]
- 카드: 제목, 작성자 닉네임, 날짜, 현재 인원/최대 인원(동행), 💬 댓글 수, 👁 조회 수
- 우측 상단: 글쓰기 버튼 (로그인 필요)

**SCR-COM-002 게시글 상세 구성**:
- 작성자 정보, 제목, 장소/날짜/인원(동행 게시글)
- 채팅방 참여 신청 버튼 (동행 게시글만, 로그인 필요 → 채팅 도메인(임하은 파트)에서 이미 구현된 API를 호출)
- 더보기(⋯): 본인 → 수정(`PUT`), 삭제(`DELETE`), 타인 → 신고
- 댓글 목록 + 댓글 입력창

**SCR-COM-003 게시글 작성 구성**:
- 게시판 선택: 동행 모집 / 자유 게시판
- 동행 모집 선택 시 추가 필드: 관광지 선택, 모임 날짜, 최대 인원, 지역
- 사진 추가 (0/5) — 최대 5장, 장당 10MB, jpg/jpeg/png/webp (Section 1-7 파일 업로드 정책 동일 적용)

---

