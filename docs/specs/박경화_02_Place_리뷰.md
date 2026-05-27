# 춘배투어 - 박경화 파트 도메인 명세서

> **용도**: Claude CLI가 읽고 구현 작업에 사용하기 위한 참조 문서  
> **담당 도메인**: 커뮤니티(게시글·댓글) / 관광지 리뷰(Place 도메인 추가 구현) / 캘린더(축제) / 신고  
> **프로젝트**: 춘배투어 (ChunBae Tour)  
> **기술 스택**: Java 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL(RDS), AWS S3  
> **Base URL**: `/api/v1`

---


---

## 2. Place 도메인 (관광지 리뷰)

> **참고**: 관광지(Place) 도메인은 김인목 파트가 담당한다.  
> 박경화 파트는 관광지 리뷰 기능만 `domain/place` 패키지 내에 추가 구현한다.

### 2-1. 패키지 구조 (리뷰 관련 파일만 박경화 추가 구현)

```
domain/place  ← 김인목 파트 도메인 (리뷰 관련 파일만 박경화가 추가 구현)
├── controller
│   └── ReviewController.java          ← 박경화 추가 구현
├── service
│   └── ReviewService.java             ← 박경화 추가 구현
├── repository
│   ├── ReviewRepository.java          ← 박경화 추가 구현
│   └── ReviewQueryRepository.java     ← 박경화 추가 구현 (QueryDSL)
├── entity
│   └── Review.java                    ← 박경화 추가 구현
├── dto
│   ├── request
│   │   ├── ReviewCreateRequest.java
│   │   └── ReviewUpdateRequest.java   ← 리뷰 수정 (rating, content, imageUrls)
│   └── response
│       └── ReviewResponse.java
└── type
    └── ReviewStatus.java              (ACTIVE, BLOCKED, DELETED)
```

### 2-2. 관광지 리뷰 API

> 영수증 사진 필수 (`receiptImageUrl`)  
> **영수증 기반 리뷰 정책**: 영수증 1개당 리뷰 1개 허용. 같은 관광지라도 다른 영수증(다른 방문)이면 추가 작성 가능 (`receiptImageUrl` 기준 중복 검사)  
> `placeId` 유효성 검증은 동일 `domain/place` 내 `PlaceService`를 호출한다

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/places/{placeId}/reviews` | 관광지 리뷰 목록 (`cursor`, `size`, `sort`) | ❌ |
| POST | `/places/{placeId}/reviews` | 관광지 리뷰 작성 | ✅ USER |
| PATCH | `/places/{placeId}/reviews/{reviewId}` | 관광지 리뷰 수정 | ✅ USER (본인) |
| DELETE | `/places/{placeId}/reviews/{reviewId}` | 관광지 리뷰 삭제 | ✅ USER (본인) - Soft Delete |

**POST `/places/{placeId}/reviews`**

Request:
```json
{
  "rating": 5,
  "content": "야간 개장 때 방문했는데 정말 아름다웠습니다!",
  "receiptImageUrl": "https://cdn.example.com/uploads/receipt_501.jpg",
  "imageUrls": ["https://cdn.example.com/uploads/review_img1.jpg"]
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "reviewId": 501,
    "placeId": 42,
    "userId": 1001,
    "nickname": "한국여행자",
    "rating": 5,
    "content": "야간 개장 때 방문했는데 정말 아름다웠습니다!",
    "receiptImageUrl": "https://cdn.example.com/uploads/receipt_501.jpg",
    "imageUrls": ["https://cdn.example.com/uploads/review_img1.jpg"],
    "createdAt": "2026-07-15T20:30:00Z"
  }
}
```

> **sort 옵션**: `LATEST` (최신순, 기본값) / `POPULAR` (인기순 — 좋아요 수 기준, 추후 좋아요 기능 연동 시 활성화)  
> **`receiptImageUrl`**: 목록 조회 시 포함하지 않는다. 개인 결제 정보이므로 리뷰 상세 조회 시에만 반환한다.

**GET `/places/{placeId}/reviews?cursor=eyJpZCI6NTAwfQ==&size=10&sort=LATEST`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "reviewId": 499,
        "userId": 1002,
        "nickname": "부산사람",
        "profileImageUrl": "https://cdn.example.com/profiles/1002.jpg",
        "rating": 4,
        "content": "역사를 느낄 수 있는 멋진 곳",
        "imageUrls": [],
        "createdAt": "2026-07-14T11:00:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6NDkwfQ==",
    "hasNext": true,
    "size": 10
  }
}
```

**PATCH `/places/{placeId}/reviews/{reviewId}`**

Request:
```json
{
  "rating": 4,
  "content": "다시 방문했는데 여전히 좋았습니다. 별점 수정합니다.",
  "imageUrls": ["https://cdn.example.com/uploads/review_img2.jpg"]
}
```

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "reviewId": 501,
    "placeId": 42,
    "rating": 4,
    "content": "다시 방문했는데 여전히 좋았습니다. 별점 수정합니다.",
    "imageUrls": ["https://cdn.example.com/uploads/review_img2.jpg"],
    "updatedAt": "2026-07-20T10:00:00Z"
  }
}
```

**DELETE `/places/{placeId}/reviews/{reviewId}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "리뷰가 삭제되었습니다."
}
```

### 2-3. Place 에러 코드 (리뷰 관련)

| 에러코드 | HTTP | 메시지 | 발생 상황 |
|---------|------|--------|----------|
| `PLACE_001` | 404 | 존재하지 않는 리뷰입니다. | 리뷰 ID 조회 시 없을 때 |
| `PLACE_002` | 409 | 이미 해당 영수증으로 리뷰를 작성했습니다. | 동일 영수증(`receiptImageUrl`) 중복 리뷰 |
| `PLACE_003` | 400 | 별점은 1점에서 5점 사이여야 합니다. | 별점 범위 초과 |
| `PLACE_004` | 400 | 영수증 사진은 필수입니다. | 리뷰 작성 시 영수증 미첨부 |
| `PLACE_005` | 403 | 해당 리뷰를 수정할 권한이 없습니다. | 타인 리뷰 수정 시도 |
| `PLACE_006` | 403 | 해당 리뷰를 삭제할 권한이 없습니다. | 타인 리뷰 삭제 시도 |
| `PLACE_007` | 400 | 리뷰 이미지는 최대 5장까지 첨부 가능합니다. | 리뷰 이미지 수 초과 |
| `PLACE_008` | 400 | 이미지 형식이 올바르지 않습니다. (jpg, jpeg, png, webp) | 허용되지 않는 이미지 형식 |

**ErrorCode Enum (Place 리뷰 부분)**:
```java
REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND,         "PLACE_001", "존재하지 않는 리뷰입니다."),
REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,     "PLACE_002", "이미 해당 영수증으로 리뷰를 작성했습니다."),
INVALID_RATING_RANGE(HttpStatus.BAD_REQUEST,   "PLACE_003", "별점은 1점에서 5점 사이여야 합니다."),
RECEIPT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "PLACE_004", "영수증 사진은 필수입니다."),
REVIEW_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN,   "PLACE_005", "해당 리뷰를 수정할 권한이 없습니다."),
REVIEW_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN,   "PLACE_006", "해당 리뷰를 삭제할 권한이 없습니다."),
REVIEW_IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "PLACE_007", "리뷰 이미지는 최대 5장까지 첨부 가능합니다."),
REVIEW_INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "PLACE_008", "이미지 형식이 올바르지 않습니다. (jpg, jpeg, png, webp)"),
```

### 2-4. 파일 업로드 정책 (리뷰 이미지)

| 항목 | 제한 |
|------|------|
| 허용 확장자 | jpg, jpeg, png, webp |
| 리뷰 이미지 | 최대 5장, 장당 10MB |
| 영수증 이미지 | 필수, 최대 5MB |
| 파일명 | UUID 기반으로 변경 저장 |
| 저장소 | AWS S3 (`global.infra.s3.S3Uploader` 사용) |

### 2-5. 화면 연결

| 화면 ID | 화면명 | 관련 API |
|--------|--------|---------|
| SCR-MAP-002 | 장소 상세 (리뷰) | `GET /places/{placeId}/reviews`, `POST /places/{placeId}/reviews`, `PATCH /places/{placeId}/reviews/{reviewId}`, `DELETE /places/{placeId}/reviews/{reviewId}` |

**SCR-MAP-002 장소 상세 구성**:
- 장소 상세 페이지 내 리뷰 탭 또는 하단 섹션
- 리뷰 목록: 작성자 닉네임, 별점, 내용, 이미지, 날짜
- 리뷰 작성 버튼 (로그인 필요)
- 영수증 사진 필수 첨부 안내

---

