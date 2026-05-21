# 06_API_명세서

# 🔥 REST API 명세서 (전체 도메인)

**작성 기준**: 제공된 유스케이스 및 도메인 구조 기반

**공통 규칙**:

- Base URL: `/api/v1`
- 인증: Bearer JWT (`Authorization: Bearer {accessToken}`)
- 페이징: Cursor 기반 (기본), 일부 API는 Offset 허용
- 날짜 형식: ISO 8601 (`2026-07-15T09:30:00Z`)
- 성공/에러 응답 공통 포맷: `{ "success": boolean, "code": "SUCCESS 또는 ERROR_CODE", "message": "설명", "data": object|null }`

---

## 페이지 / 권한 구조

일반 사용자 페이지, 상인 페이지, 관리자 페이지는 분리한다. 로그인은 가능하면 통합하고, API 접근 권한은 `USER`, `MERCHANT`, `ADMIN` Role 기반으로 제한한다.

```
일반 회원가입
→ USER 계정 생성
→ 상인 등록 신청
→ 관리자 승인
→ role이 MERCHANT로 변경
→ 상인 페이지 접근 가능
```

상인 본인용 API와 관리자 관리용 API는 분리한다.

```
상인 본인용: GET /api/v1/merchants/me/shop
관리자 관리용: GET /api/v1/admin/shops/{shopId}
관리자 관리용: PATCH /api/v1/admin/shops/{shopId}
```

| Role | 기능 범위 |
| --- | --- |
| USER | 일반 사용자 기능, 프로필, 찜, 리뷰, 결제 |
| MERCHANT | USER 기능 포함, 내 가게 관리, 메뉴 관리, 광고 신청, 정산 신청 |
| ADMIN | 관리자 대시보드, 유저 관리, 상인 승인/거절, 가게 관리, 신고 처리, 광고/정산/인증 심사 |

---

## 공통 응답 Wrapper 기준

모든 REST API 응답은 아래 형식을 기준으로 한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

에러 응답도 동일한 Wrapper를 사용한다.

```json
{
  "success": false,
  "code": "CHAT_001",
  "message": "존재하지 않는 채팅방입니다.",
  "data": null
}
```

문서 내 예시 JSON도 위 Wrapper 기준에 맞춰 `success`, `code`, `message`, `data`를 함께 표기한다.

---

## 공통 페이징 Request / Response 구조

Cursor 기반 Request Query Parameters

```json
{

"cursor": "eyJpZCI6MTAwfQ==",

"size": 20

}
```

Cursor 기반 Response Wrapper

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [],

"nextCursor": "eyJpZCI6MTIwfQ==",

"hasNext": true,

"size": 20

}

}
```

---

## 📌 0. 홈 / 대시보드

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/users/me/home` | 사용자 홈 데이터 조회 | - | 배너, 추천 관광지, 인기 축제, 최근 알림 | ✅ USER | 🟢 캐싱(TTL 5분) |
| GET | `/merchants/me/home` | 상인 홈 데이터 조회 | - | 오늘 매출, 최근 주문, 리뷰 현황 | ✅ MERCHANT | 🟢 캐싱(TTL 3분) |
| GET | `/admin/dashboard` | 관리자 대시보드 | - | 전체 통계, 미처리 신고, 신규 가입 등 | ✅ ADMIN | 🟢 캐싱(TTL 1분) |

### 예시 JSON

**GET `/users/me/home`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"banners": [

  { "bannerId": 1, "imageUrl": "https://cdn.example.com/banner1.jpg", "linkUrl": "/events/5" }

],

"recommendedPlaces": [

  { "placeId": 42, "name": "경복궁", "imageUrl": "https://cdn.example.com/gyeongbok.jpg", "rating": 4.7, "likeCount": 1230 }

],

"upcomingFestivals": [

  { "festivalId": 7, "name": "보령머드축제", "startDate": "2026-07-18", "endDate": "2026-07-27", "region": "충남 보령" }

],

"recentNotifications": [

  { "notificationId": 99, "type": "CHAT_MESSAGE", "message": "새 채팅 메시지가 도착했습니다.", "isRead": false, "createdAt": "2026-07-15T09:30:00Z" }

]

}

}
```

**GET `/merchants/me/home`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"todaySales": { "totalAmount": 150000, "orderCount": 12 },

"yeopjeonBalance": 320000,

"recentOrders": [

  { "orderId": "ORD-20260715-001", "buyerNickname": "여행자1", "amount": 15000, "productName": "떡볶이 쿠폰", "createdAt": "2026-07-15T08:20:00Z" }

],

"pendingQrPayments": 2,

"recentReviews": [

  { "reviewId": 55, "rating": 5, "content": "맛있어요!", "writerNickname": "여행자2", "createdAt": "2026-07-14T18:00:00Z" }

]

}

}
```

**GET `/admin/dashboard`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"totalUsers": 15230,

"newUsersToday": 42,

"totalMerchants": 380,

"pendingMerchantApplications": 5,

"pendingReports": 12,

"pendingRefunds": 3,

"todayRevenue": 2500000,

"activeChatRooms": 67

}

}
```

---

## 🔐 1. 인증 / 회원 (Auth / User)

### 1-1. Auth

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/auth/signup` | 사용자 회원가입 | email, password, nickname, language | userId, email, nickname | ❌ | - |
| POST | `/auth/login` | 통합 로그인 | email, password | accessToken, refreshToken (Cookie), role | ❌ | 로그인은 통합하고 API 접근은 Role 기반으로 제한 |
| POST | `/auth/reissue` | Access Token 재발급 | refreshToken (Cookie) | accessToken | ❌ | Cookie에서 refresh_token 읽음 |
| POST | `/auth/logout` | 로그아웃 | - | - | ✅ ALL | 서버에서 refresh_token 무효화 |

### 예시 JSON

**POST `/users/auth/signup`**

Request

```json
{

"email": "traveler@example.com",

"password": "SecurePass123!",

"nickname": "한국여행자",

"language": "ko"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"userId": 1001,

"email": "traveler@example.com",

"nickname": "한국여행자"

}

}
```

**POST `/auth/login`**

Request

```json
{

"email": "traveler@example.com",

"password": "SecurePass123!"

}
```

Response 200

Set-Cookie: refresh_token=eyJ…; HttpOnly; Secure; SameSite=Strict; Path=/api/v1

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"accessToken": "eyJhbGciOiJIUzI1NiIs...",

"userId": 1001,

"nickname": "한국여행자",

"role": "USER"

}

}
```

**POST `/auth/reissue`**

Request: Cookie header에 refresh_token 포함

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"accessToken": "eyJhbGciOiJIUzI1NiIs_NEW_TOKEN..."

}

}
```

**POST `/auth/logout`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": null

}
```

---

### 1-2. User (내 정보)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/users/me` | 내 프로필 조회 | - | 프로필 전체 정보 | ✅ USER | - |
| PATCH | `/users/me` | 내 프로필 수정 | nickname?, profileImageUrl?, language? | 수정된 프로필 | ✅ USER | - |
| GET | `/users/{userId}/companion-profile` | 다른 사용자의 동행 프로필 조회 | - | 공개 프로필, 동행 점수 | ✅ USER | 채팅 참여 신청자 프로필 조회 시 사용 |
| GET | `/users/me/likes` | 내 찜 목록 조회 | cursor, size | 찜한 관광지 리스트 | ✅ USER | Cursor 페이징 |
| GET | `/users/me/reviews` | 내 리뷰 조회 | cursor, size | 작성한 리뷰 리스트 | ✅ USER | Cursor 페이징 |

### 예시 JSON

**GET `/users/me`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"userId": 1001,

"email": "traveler@example.com",

"nickname": "한국여행자",

"profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",

"language": "ko",

"companionScore": 4.3,

"companionReviewCount": 12,

"yeopjeonBalance": 50000,

"createdAt": "2026-01-10T12:00:00Z"

}

}
```

**PATCH `/users/me`**

Request

```json
{

"nickname": "서울탐험가",

"language": "en"

}
```

Response 200

```json
{
"success": true,
"code": "SUCCESS",
"message": "요청이 성공적으로 처리되었습니다.",
"data": {
"userId": 1001,
"nickname": "서울탐험가",
"profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",
"language": "en"
}
}
```

**GET `/users/me/likes?cursor=eyJpZCI6NTB9&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "placeId": 42,

    "name": "경복궁",

    "imageUrl": "https://cdn.example.com/gyeongbok.jpg",

    "category": "TOURIST_SPOT",

    "rating": 4.7,

    "likedAt": "2026-07-10T14:00:00Z"

  }

],

"nextCursor": "eyJpZCI6NDB9",

"hasNext": true,

"size": 10

}

}
```

**GET `/users/me/reviews?cursor=eyJpZCI6MjB9&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "reviewId": 88,

    "placeId": 42,

    "placeName": "경복궁",

    "rating": 5,

    "content": "정말 멋진 곳이었습니다!",

    "imageUrls": ["https://cdn.example.com/reviews/88_1.jpg"],

    "createdAt": "2026-07-12T16:30:00Z"

  }

],

"nextCursor": "eyJpZCI6MTB9",

"hasNext": false,

"size": 10

}

}
```

---

## 🗺️ 2. 관광지 / 지도 (Place / Map)

### 2-1. Places

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/places/nearby` | 주변 관광지 조회 | lat, lng, radius, cursor, size | 관광지 리스트 (거리 포함) | ❌ | 🟢 캐싱(Redis GEO), 외부 API fallback |
| GET | `/places/{placeId}` | 관광지 상세 조회 | - | 관광지 상세 정보 | ❌ | 🟢 캐싱(TTL 10분) |
| GET | `/places/{placeId}/reviews` | 관광지 리뷰 목록 | cursor, size, sort | 리뷰 리스트 | ❌ | 🟢 캐싱(TTL 3분), 커뮤니티 도메인 연결 |
| POST | `/places/{placeId}/reviews` | 관광지 리뷰 작성 | rating, content, receiptImageUrl, imageUrls[] | 생성된 리뷰 | ✅ USER | 영수증 사진 필수, 결제/방문 이력 검증은 MVP 제외 |
| GET | `/places/{placeId}/nearby-shops` | 주변 상점 조회 | radius? | 상점 리스트 | ❌ | 🟢 캐싱, 상인 도메인 연결 |
| GET | `/places/{placeId}/recommend` | 관광지 기반 추천 | - | 추천 관광지 리스트 | ❌ | 🟢 캐싱(TTL 30분), 유사 카테고리+위치 |

### 예시 JSON

**GET `/places/nearby?lat=37.5796&lng=126.9770&radius=3000&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "placeId": 42,

    "name": "경복궁",

    "category": "TOURIST_SPOT",

    "imageUrl": "https://cdn.example.com/gyeongbok.jpg",

    "latitude": 37.5796,

    "longitude": 126.9770,

    "rating": 4.7,

    "reviewCount": 342,

    "likeCount": 1230,

    "distanceMeters": 150

  },

  {

    "placeId": 43,

    "name": "북촌한옥마을",

    "category": "TOURIST_SPOT",

    "imageUrl": "https://cdn.example.com/bukchon.jpg",

    "latitude": 37.5826,

    "longitude": 126.9831,

    "rating": 4.5,

    "reviewCount": 256,

    "likeCount": 890,

    "distanceMeters": 720

  }

],

"nextCursor": "eyJkaXN0IjowLjcyfQ==",

"hasNext": true,

"size": 10

}

}
```

**GET `/places/42`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"placeId": 42,

"name": "경복궁",

"description": "조선 시대의 법궁으로, 서울에서 가장 큰 궁궐입니다.",

"category": "TOURIST_SPOT",

"address": "서울 종로구 사직로 161",

"latitude": 37.5796,

"longitude": 126.9770,

"imageUrls": [

  "https://cdn.example.com/gyeongbok_1.jpg",

  "https://cdn.example.com/gyeongbok_2.jpg"

],

"operatingHours": "09:00 ~ 18:00 (화요일 휴관)",

"admissionFee": "3000원",

"phone": "02-3700-3900",

"rating": 4.7,

"reviewCount": 342,

"likeCount": 1230,

"isLiked": false

}

}
```

**POST `/places/42/reviews`**

Request

```json
{

"rating": 5,

"content": "야간 개장 때 방문했는데 정말 아름다웠습니다!",

"receiptImageUrl": "https://cdn.example.com/uploads/receipt_501.jpg",

"imageUrls": [

"https://cdn.example.com/uploads/review_img1.jpg"

]

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

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

**GET `/places/42/reviews?cursor=eyJpZCI6NTAwfQ==&size=10&sort=LATEST`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

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

**GET `/places/42/nearby-shops?radius=1000`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "shopId": 15,

  "shopId": 201,

  "name": "광화문 떡볶이",

  "category": "FOOD",

  "imageUrl": "https://cdn.example.com/shops/15.jpg",

  "rating": 4.6,

  "isCertified": true,

  "distanceMeters": 320,

  "latitude": 37.5780,

  "longitude": 126.9765

}

]

}
```

**GET `/places/42/recommend`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "placeId": 43,

  "name": "북촌한옥마을",

  "category": "TOURIST_SPOT",

  "imageUrl": "https://cdn.example.com/bukchon.jpg",

  "rating": 4.5,

  "distanceMeters": 720,

  "reason": "SAME_CATEGORY"

},

{

  "placeId": 44,

  "name": "창덕궁",

  "category": "TOURIST_SPOT",

  "imageUrl": "https://cdn.example.com/changdeok.jpg",

  "rating": 4.8,

  "distanceMeters": 1100,

  "reason": "NEARBY_POPULAR"

}

]

}
```

### 2-2. Directions

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/directions` | 길찾기 URL 생성 | originLat, originLng, destLat, destLng | KakaoMap redirect URL | ❌ | MVP는 KakaoMap API만 사용 |

**GET `/directions?originLat=37.5665&originLng=126.9780&destLat=37.5796&destLng=126.9770`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"provider": "KAKAO",

"redirectUrl": "https://map.kakao.com/link/to/경복궁,37.5796,126.9770"

}

}
```

---

## 🔍 3. 검색 (Search)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/search/places` | 관광지 키워드 검색 | q, category?, region?, cursor, size | 관광지 리스트 | ❌ | DB → QueryDSL 기반 검색/인기 검색어 Redis ZSET 사용/외부 검색 API 연동은 추후 확장 |
| GET | `/search/festivals` | 축제 검색 | q?, startDate?, endDate?, region?, cursor, size | 축제 리스트 | ❌ | 날짜/지역 필터 |
| GET | `/search/suggest` | 검색어 자동완성 | q | 추천 검색어 리스트 | ❌ | 🟢 캐싱(Redis prefix) |
| GET | `/search/popular` | 인기 검색어 조회 | - | TOP 10 검색어 | ❌ | 🟢 캐싱(Redis ZSET, TTL 1시간) |
| GET | `/search/recent` | 최근 검색어 조회 | - | 최근 검색어 리스트 | ✅ USER | Redis LIST |
| POST | `/search` | 검색어 저장/로그 | keyword | - | ✅ USER | 인기 검색어 집계 반영 |

### 예시 JSON

**GET `/search/places?q=궁궐&category=TOURIST_SPOT&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "placeId": 42,

    "name": "경복궁",

    "category": "TOURIST_SPOT",

    "address": "서울 종로구 사직로 161",

    "imageUrl": "https://cdn.example.com/gyeongbok.jpg",

    "rating": 4.7,

    "reviewCount": 342

  }

],

"nextCursor": "eyJpZCI6NDJ9",

"hasNext": true,

"size": 10

}

}
```

**GET `/search/suggest?q=경복`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": ["경복궁", "경복궁 야간개장", "경복궁 한복체험"]

}
```

**GET `/search/popular`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{ "rank": 1, "keyword": "제주도", "searchCount": 5420, "changeType": "SAME" },

{ "rank": 2, "keyword": "경복궁", "searchCount": 4310, "changeType": "UP" },

{ "rank": 3, "keyword": "부산 해운대", "searchCount": 3870, "changeType": "NEW" }

]

}
```

**GET `/search/recent`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{ "keyword": "남산타워", "searchedAt": "2026-07-15T10:00:00Z" },

{ "keyword": "명동 맛집", "searchedAt": "2026-07-15T09:30:00Z" }

]

}
```

**POST `/search`**

Request

```json
{

"keyword": "경복궁"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": null

}
```

---

## 📅 4. 축제 / 캘린더 (Festival / Calendar)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/festivals` | 축제 목록 조회 (캘린더 일별 클릭 시 사용) | `date?`, `region?`, `cursor?`, `size?` | 축제 리스트 (Paging) | ❌ | 🟢 캐싱(TTL 1시간), TourAPI 확장 가능 |
| POST | `/festivals` | 축제 등록 (캘린더 일정 추가) | Request Body | 등록된 축제 정보 | ⭕ | 권한 확인 필요 |
| GET | `/festivals/{festivalId}` | 축제 단건 상세 조회 | `festivalId` (Path) | 축제 상세 정보 | ❌ | 🟢 캐싱(TTL 1시간) |
| PUT | `/festivals/{festivalId}` | 축제 수정 (캘린더 일정 수정) | `festivalId` (Path), Request Body | 수정된 축제 정보 | ⭕ | 권한 확인 필요 |
| DELETE | `/festivals/{festivalId}` | 축제 삭제 (캘린더 일정 삭제) | `festivalId` (Path) | - | ⭕ | 권한 확인 필요 |
| GET | `/calendar` | 월별 캘린더 조회 | `year`, `month` | 날짜별 축제/행사 맵 | ❌ | 🟢 캐싱(TTL 1시간) |

### 진행 상태 기준

`status`는 관리자 노출/삭제 상태로 `ACTIVE / HIDDEN / DELETED`만 사용한다. `progressStatus`는 축제 시작일/종료일 기준으로 API 응답에서 계산해서 내려준다.

| 필드 | 허용 값 | 설명 |
| --- | --- | --- |
| status | ACTIVE, HIDDEN, DELETED | 관리자 운영 상태 |
| progressStatus | UPCOMING, IN_PROGRESS, ENDED | 날짜 기준 계산값 |

### 예시 JSON

**GET `/festivals?region=서울&date=2026-07-15&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "festivalId": 7,

    "name": "서울 한강 페스티벌",

    "description": "한강에서 즐기는 여름 축제",

    "region": "서울",

    "address": "서울 영등포구 여의동로 330",

    "startDate": "2026-07-10",

    "endDate": "2026-07-20",

    "imageUrl": "https://cdn.example.com/festivals/7.jpg",

    "status": "ACTIVE",

    "progressStatus": "IN_PROGRESS"

  }

],

"nextCursor": "eyJpZCI6N30=",

"hasNext": false,

"size": 10

}

}
```

**GET `/calendar?year=2026&month=7`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"year": 2026,

"month": 7,

"events": {

  "2026-07-10": [

    { "festivalId": 7, "name": "서울 한강 페스티벌", "type": "FESTIVAL" }

  ],

  "2026-07-18": [

    { "festivalId": 8, "name": "보령머드축제", "type": "FESTIVAL" },

    { "eventId": 3, "name": "전통시장 야시장", "type": "EVENT" }

  ]

}

}

}
```

---

## ⭐ 5. 추천 (Recommend)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/recommend/popular` | 인기 관광지 추천 | size? | 인기 관광지 리스트 | ❌ | 🟢 캐싱(Redis ZSET, TTL 30분) |
| GET | `/recommend/nearby` | 위치 기반 추천 | lat, lng, size? | 추천 관광지 리스트 | ❌ | 🟢 캐싱(Redis GEO) |
| GET | `/recommend/category` | 카테고리 기반 추천 | category, size? | 추천 관광지 리스트 | ❌ | 🟢 캐싱(TTL 30분), category는 TOURIST_SPOT/TRADITIONAL_MARKET만 허용 |
| GET | `/recommend/personal` | 개인화 추천 | size? | 추천 관광지 리스트 | ✅ USER | ❌ 추후 확장, MVP 제외 |

### 예시 JSON

**GET `/recommend/popular?size=5`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "placeId": 42,

  "name": "경복궁",

  "category": "TOURIST_SPOT",

  "imageUrl": "https://cdn.example.com/gyeongbok.jpg",

  "rating": 4.7,

  "likeCount": 1230,

  "viewCount": 52000,

  "rank": 1

}

]

}
```

**GET `/recommend/nearby?lat=37.5665&lng=126.9780&size=5`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "placeId": 42,

  "name": "경복궁",

  "category": "TOURIST_SPOT",

  "imageUrl": "https://cdn.example.com/gyeongbok.jpg",

  "rating": 4.7,

  "distanceMeters": 500,

  "reason": "NEARBY_HIGH_RATING"

}

]

}
```

**GET `/recommend/personal?size=5`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "placeId": 55,

  "name": "전주 한옥마을",

  "category": "TOURIST_SPOT",

  "imageUrl": "https://cdn.example.com/jeonju.jpg",

  "rating": 4.6,

  "reason": "LIKED_SIMILAR_CATEGORY",

  "score": 0.87

}

]

}
```

---

## ❤️ 6. 찜 (Like)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/places/{placeId}/like` | 관광지 찜 추가 | - | - | ✅ USER | 🔴 동시성(중복 찜 방지, UNIQUE 제약) |
| DELETE | `/places/{placeId}/like` | 관광지 찜 해제 | - | - | ✅ USER | - |

### 예시 JSON

**POST `/places/42/like`**

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"placeId": 42,

"liked": true,

"totalLikeCount": 1231

}

}
```

**DELETE `/places/42/like`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"placeId": 42,

"liked": false,

"totalLikeCount": 1230

}

}
```

---

## 📝 7. 커뮤니티 (Community - Posts)

### 7-1. 동행 게시판

> 동행 게시글 `status`는 `ACTIVE / BLOCKED / DELETED`만 사용한다. `BLOCKED`는 신고 처리로 사용자 화면에서 숨김 처리된 상태다. 모집 진행 여부는 별도 모집 상태값을 두지 않고 현재 인원/최대 인원 및 연결된 채팅방 상태로 판단한다.
> 

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/posts/companions` | 동행 게시글 생성 | title, content, placeId, meetingDate, maxMembers | 생성된 게시글 | ✅ USER | - |
| GET | `/posts/companions` | 동행 게시글 목록 | region?, meetingDate?, cursor, size | 게시글 리스트 | ❌ | Cursor 페이징 |
| GET | `/posts/companions/{id}` | 동행 게시글 상세 | - | 게시글 상세 | ❌ | - |
| PUT | `/posts/companions/{id}` | 동행 게시글 수정 | title, content, placeId, meetingDate, maxMembers | 수정된 게시글 | ✅ USER (본인) | - |
| DELETE | `/posts/companions/{id}` | 동행 게시글 삭제 | - | - | ✅ USER (본인) | - |

### 예시 JSON

**POST `/posts/companions`**

Request

```json
{

"title": "7/20 경복궁 같이 가실 분!",

"content": "오전 10시에 경복궁 앞에서 만나서 같이 둘러볼 분 구합니다. 한복 체험도 할 예정이에요!",

"placeId": 42,

"meetingDate": "2026-07-20",

"maxMembers": 4

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"postId": 301,

"title": "7/20 경복궁 같이 가실 분!",

"content": "오전 10시에 경복궁 앞에서 만나서 같이 둘러볼 분 구합니다. 한복 체험도 할 예정이에요!",

"placeId": 42,

"placeName": "경복궁",

"meetingDate": "2026-07-20",

"maxMembers": 4,

"currentMembers": 1,

"status": "ACTIVE",

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

**GET `/posts/companions?cursor=eyJpZCI6MzAwfQ==&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "postId": 299,

    "title": "제주도 올레길 같이 걸어요",

    "placeName": "올레길 7코스",

    "meetingDate": "2026-07-25",

    "maxMembers": 3,

    "currentMembers": 2,

    "status": "ACTIVE",

    "writer": {

      "userId": 1005,

      "nickname": "제주사랑",

      "profileImageUrl": "https://cdn.example.com/profiles/1005.jpg",

      "companionScore": 4.8

    },

    "commentCount": 5,

    "createdAt": "2026-07-14T18:00:00Z"

  }

],

"nextCursor": "eyJpZCI6Mjk5fQ==",

"hasNext": true,

"size": 10

}

}
```

### 7-2. 자유 게시판

> 관광지 별점 리뷰는 `/places/{placeId}/reviews`를 사용한다.
> 
> 
> 커뮤니티 영역의 일반 게시글은 자유 게시판으로 분리하고 `/posts/free`를 사용한다.
> 

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/posts/free` | 자유 게시글 생성 | title, content, imageUrls[] | 생성된 게시글 | ✅ USER | - |
| GET | `/posts/free` | 자유 게시글 목록 | cursor, size, sort | 게시글 리스트 | ❌ | Cursor 페이징 |
| GET | `/posts/free/{id}` | 자유 게시글 상세 | - | 게시글 상세 | ❌ | - |
| PUT | `/posts/free/{id}` | 자유 게시글 수정 | title, content, imageUrls[] | 수정된 게시글 | ✅ USER (본인) | - |
| DELETE | `/posts/free/{id}` | 자유 게시글 삭제 | - | - | ✅ USER (본인) | Soft Delete |

### 예시 JSON

**POST `/posts/free`**

Request

```json
{

"title": "전주 한옥마을 여행 팁 공유",

"content": "주말에는 사람이 많아서 오전에 방문하는 걸 추천합니다.",

"imageUrls": [

"https://cdn.example.com/uploads/post_img1.jpg"

]

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

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

"createdAt": "2026-07-15T21:00:00Z"

}

}
```

---

## 💬 8. 댓글 (Comment)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/comments` | 댓글 생성 | postId, content, parentCommentId? | 생성된 댓글 | ✅ USER | 대댓글 지원 |
| GET | `/comments` | 댓글 목록 조회 | postId, cursor, size | 댓글 리스트 (대댓글 포함) | ❌ | Cursor 페이징 |
| PATCH | `/comments/{id}` | 댓글 수정 | content | 수정된 댓글 | ✅ USER (본인) | - |
| DELETE | `/comments/{id}` | 댓글 삭제 | - | - | ✅ USER (본인) | Soft delete |

### 예시 JSON

**POST `/comments`**

Request

```json
{

"postId": 301,

"content": "저도 참여하고 싶어요! 한복 체험 좋아합니다.",

"parentCommentId": null

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

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

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

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

---

## 🚨 9. 신고 (Report)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/reports` | 신고 생성 | targetType, targetId, reason, description | 생성된 신고 | ✅ USER | targetType: POST, COMMENT, REVIEW, USER |
| GET | `/admin/reports` | 신고 목록 조회 | status?, cursor, size | 신고 리스트 | ✅ ADMIN | Cursor 페이징 |
| GET | `/admin/reports/{reportId}` | 신고 상세 조회 | - | 신고 상세 | ✅ ADMIN | - |
| POST | `/admin/reports/{id}/resolve` | 신고 처리 | action, adminNote | 처리 결과 | ✅ ADMIN | action: WARNING, SUSPEND, DELETE, DISMISS |

### 예시 JSON

**POST `/reports`**

Request

```json
{

"targetType": "COMMENT",

"targetId": 805,

"reason": "SPAM",

"description": "광고성 댓글입니다."

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"reportId": 150,

"targetType": "COMMENT",

"targetId": 805,

"reason": "SPAM",

"status": "PENDING",

"createdAt": "2026-07-15T12:00:00Z"

}

}
```

**POST `/admin/reports/150/resolve`**

Request

```json
{

"action": "DELETE",

"adminNote": "스팸 확인, 해당 댓글 삭제 처리"

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"reportId": 150,

"status": "RESOLVED",

"action": "DELETE",

"adminNote": "스팸 확인, 해당 댓글 삭제 처리",

"resolvedAt": "2026-07-15T14:00:00Z",

"resolvedBy": "admin01"

}

}
```

---

## 💰 10. 결제 / 엽전 (Payment / Yeopjeon)

### 10-1. Payment

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/payments/charge` | 엽전 충전 요청 | amount, paymentMethod | orderId, 결제 정보 | ✅ USER | 🔴 동시성(멱등성 키 필수) |
| POST | `/payments/callback/success` | 결제 성공 콜백 | orderId, paymentKey, amount | 충전 결과 | ❌ (서버간) | PG 콜백, 서명 검증 |
| POST | `/payments/callback/fail` | 결제 실패 콜백 | orderId, errorCode, errorMessage | - | ❌ (서버간) | PG 콜백 |
| GET | `/payments/history` | 결제 내역 조회 | cursor, size | 결제 내역 리스트 | ✅ USER | Cursor 페이징 |
| POST | `/payments/{orderId}/refund` | 환불 요청 | reason | 환불 요청 정보 | ✅ USER | 미사용 엽전 전액만 가능 |
| PATCH | `/payments/refund/{refundId}/cancel` | 환불 취소 | - | - | ✅ USER | - |
| GET | `/admin/refunds` | 환불 목록 조회 | status?, cursor, size | 환불 리스트 | ✅ ADMIN | Cursor 페이징 |
| PATCH | `/admin/refunds/{refundId}/approve` | 환불 승인 | - | 승인 결과 | ✅ ADMIN | 🔴 동시성(상태 전이 락) |
| PATCH | `/admin/refunds/{refundId}/reject` | 환불 거절 | rejectReason | 거절 결과 | ✅ ADMIN | - |

### 예시 JSON

**POST `/payments/charge`**

Request

Header: Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

```json
{

"amount": 50000,

"paymentMethod": "CARD"

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"orderId": "ORD-20260715-ABC123",

"amount": 50000,

"paymentMethod": "CARD",

"status": "PENDING",

"pgRedirectUrl": "https://pg.example.com/pay?orderId=ORD-20260715-ABC123",

"createdAt": "2026-07-15T12:00:00Z"

}

}
```

**POST `/payments/callback/success`**

Request (PG사에서 호출)

```json
{

"orderId": "ORD-20260715-ABC123",

"paymentKey": "PK-XYZ789",

"amount": 50000

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"orderId": "ORD-20260715-ABC123",

"status": "COMPLETED",

"chargedYeopjeon": 50000,

"newBalance": 100000

}

}
```

**GET `/payments/history?cursor=eyJpZCI6NTB9&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "paymentId": 49,

    "orderId": "ORD-20260715-ABC123",

    "amount": 50000,

    "paymentMethod": "CARD",

    "status": "COMPLETED",

    "createdAt": "2026-07-15T12:00:00Z"

  }

],

"nextCursor": "eyJpZCI6NDl9",

"hasNext": true,

"size": 10

}

}
```

**POST `/payments/ORD-20260715-ABC123/refund`**

Request

```json
{

"reason": "단순 변심으로 환불 요청합니다."

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"refundId": 25,

"orderId": "ORD-20260715-ABC123",

"amount": 50000,

"reason": "단순 변심으로 환불 요청합니다.",

"status": "PENDING",

"createdAt": "2026-07-15T15:00:00Z"

}

}
```

### 10-2. Yeopjeon (엽전)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/yeopjeon/balance` | 잔액 조회 | - | 잔액 정보 | ✅ USER | - |
| GET | `/yeopjeon/history` | 사용 내역 조회 | type?, cursor, size | 사용 내역 리스트 | ✅ USER | Cursor 페이징 |
| GET | `/yeopjeon/qr/shops/{shopId}` | 상인 QR코드 조회 | - | QR 정보 | ✅ USER | 고정 QR, 사용자가 스캔 |
| POST | `/yeopjeon/qr/pay` | QR 결제 요청 | shopId, amount | 결제 요청 정보 | ✅ USER | 🔴 동시성(잔액 차감, 비관적 락) |
| POST | `/yeopjeon/qr/confirm` | QR 결제 승인 | paymentRequestId | 승인 결과 | ✅ MERCHANT | 🔴 동시성(상태 전이 락) |
| POST | `/yeopjeon/qr/reject` | QR 결제 거절 | paymentRequestId, reason | 거절 결과 | ✅ MERCHANT | 잔액 복구 |

### 예시 JSON

**GET `/yeopjeon/balance`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"userId": 1001,

"balance": 100000,

"lastUpdatedAt": "2026-07-15T12:05:00Z"

}

}
```

**GET `/yeopjeon/history?type=USE&cursor=eyJpZCI6MzB9&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "historyId": 29,

    "type": "USE",

    "amount": -15000,

    "balance": 85000,

    "description": "광화문 떡볶이 QR결제",

    "merchantName": "광화문 떡볶이",

    "createdAt": "2026-07-15T13:00:00Z"

  },

  {

    "historyId": 28,

    "type": "CHARGE",

    "amount": 50000,

    "balance": 100000,

    "description": "엽전 충전",

    "merchantName": null,

    "createdAt": "2026-07-15T12:05:00Z"

  }

],

"nextCursor": "eyJpZCI6Mjh9",

"hasNext": true,

"size": 10

}

}
```

**GET `/yeopjeon/qr/shops/201`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"shopId": 201,

"shopName": "광화문 떡볶이",

"qrCodeUrl": "https://cdn.example.com/qr/shop_201.png",

"qrPayload": "YEOPJEON_PAY:SHOP:201"

}

}
```

**POST `/yeopjeon/qr/pay`**

Request

```json
{

"shopId": 201,

"amount": 15000

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"paymentRequestId": "QR-20260715-DEF456",

"shopId": 201,

"shopName": "광화문 떡볶이",

"amount": 15000,

"status": "PENDING_CONFIRM",

"expiresAt": "2026-07-15T13:05:00Z"

}

}
```

**POST `/yeopjeon/qr/confirm`**

Request

```json
{

"paymentRequestId": "QR-20260715-DEF456"

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"paymentRequestId": "QR-20260715-DEF456",

"status": "COMPLETED",

"amount": 15000,

"buyerNickname": "한국여행자",

"confirmedAt": "2026-07-15T13:01:00Z"

}

}
```

---

## 🏪 11. 스토어 (Store)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/store/products` | 상품 목록 조회 | category?, cursor, size | 상품 리스트 | ❌ | 🟢 캐싱(TTL 5분) |
| GET | `/store/products/{productId}` | 상품 상세 조회 | - | 상품 상세 | ❌ | 🟢 캐싱(TTL 5분) |
| POST | `/store/products/{productId}/purchase` | 상품 구매 | quantity | 구매 결과 | ✅ USER | 🔴 동시성(재고 차감, 비관적 락 + 엽전 차감) |
| GET | `/store/orders` | 구매 내역 조회 | cursor, size | 구매 내역 리스트 | ✅ USER | Cursor 페이징 |
| GET | `/store/my-items` | 보유 쿠폰/투어권 | cursor, size | 보유 아이템 리스트 | ✅ USER | Cursor 페이징 |

### 예시 JSON

**GET `/store/products?category=COUPON&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "productId": 10,

    "name": "광화문 떡볶이 3000원 할인 쿠폰",

    "category": "COUPON",

    "price": 2000,

    "originalPrice": 3000,

    "imageUrl": "https://cdn.example.com/products/10.jpg",

    "merchantName": "광화문 떡볶이",

    "stock": 50,

    "soldCount": 120

  }

],

"nextCursor": "eyJpZCI6MTB9",

"hasNext": true,

"size": 10

}

}
```

**POST `/store/products/10/purchase`**

Request

```json
{

"quantity": 1

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"orderId": "STORE-20260715-GHI789",

"productId": 10,

"productName": "광화문 떡볶이 3000원 할인 쿠폰",

"quantity": 1,

"totalPrice": 2000,

"remainingBalance": 83000,

"status": "COMPLETED",

"createdAt": "2026-07-15T14:00:00Z"

}

}
```

**GET `/store/my-items?size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "itemId": 601,

    "productId": 10,

    "productName": "광화문 떡볶이 3000원 할인 쿠폰",

    "type": "COUPON",

    "status": "UNUSED",

    "merchantName": "광화문 떡볶이",

    "expiresAt": "2026-08-15T23:59:59Z",

    "purchasedAt": "2026-07-15T14:00:00Z"

  }

],

"nextCursor": "eyJpZCI6NjAxfQ==",

"hasNext": false,

"size": 10

}

}
```

---

## 🏬 12. 상인 (Merchant)

### 12-1. 상인 기본

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/merchants/apply` | 상인 등록 신청 | shopName, businessNumber, category, address, phone, description | 신청 정보 | ✅ USER | 서류 첨부 포함 |
| GET | `/merchants/me/shop` | 가게 정보 조회 | - | 가게 상세 | ✅ MERCHANT | - |
| PATCH | `/merchants/me/shop` | 가게 정보 수정 | description?, phone?, operatingHours? | 수정된 정보 | ✅ MERCHANT | - |
| POST | `/merchants/me/shop/images` | 가게 사진 업로드 | multipart/form-data (images) | 업로드된 이미지 URL | ✅ MERCHANT | - |
| PATCH | `/merchants/me/shop/account` | 정산 계좌 등록/변경 | bankCode, accountNumber, accountHolder | 등록 결과 | ✅ MERCHANT | - |
| POST | `/merchants/me/shop/ads/{adId}/extend` | 광고 연장 | extensionDays | 연장 결과 | ✅ MERCHANT | 🔴 동시성(엽전 차감) |

### 예시 JSON

**POST `/merchants/apply`**

Request (multipart/form-data)

```json
{

"shopName": "광화문 떡볶이",

"businessNumber": "123-45-67890",

"category": "FOOD",

"address": "서울 종로구 세종대로 172",

"phone": "02-1234-5678",

"description": "전통 떡볶이 전문점입니다.",

"documents": ["https://cdn.example.com/docs/business-license.pdf"]

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"applicationId": 50,

"shopName": "광화문 떡볶이",

"status": "PENDING",

"createdAt": "2026-07-15T10:00:00Z"

}

}
```

**GET `/merchants/me/shop`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"shopId": 201,

"shopName": "광화문 떡볶이",

"category": "FOOD",

"address": "서울 종로구 세종대로 172",

"phone": "02-1234-5678",

"description": "전통 떡볶이 전문점입니다.",

"operatingHours": "10:00 ~ 21:00",

"imageUrls": [

  "https://cdn.example.com/shops/201_1.jpg",

  "https://cdn.example.com/shops/201_2.jpg"

],

"isCertified": true,

"rating": 4.6,

"reviewCount": 85,

"yeopjeonBalance": 320000,

"settlementAccount": {

  "bankCode": "088",

  "accountNumber": "****5678",

  "accountHolder": "홍길동"

}

}

}
```

### 12-2. 메뉴

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/merchants/me/shop/menus` | 메뉴 추가 | name, price, description, imageUrl | 생성된 메뉴 | ✅ MERCHANT | - |
| PATCH | `/merchants/me/shop/menus/{menuId}` | 메뉴 수정 | name?, price?, description?, imageUrl? | 수정된 메뉴 | ✅ MERCHANT | - |
| DELETE | `/merchants/me/shop/menus/{menuId}` | 메뉴 삭제 | - | - | ✅ MERCHANT | - |

**POST `/merchants/me/shop/menus`**

Request

```json
{

"name": "떡볶이",

"price": 8000,

"description": "매콤달콤한 전통 떡볶이",

"imageUrl": "https://cdn.example.com/menus/tteokbokki.jpg"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"menuId": 401,

"name": "떡볶이",

"price": 8000,

"description": "매콤달콤한 전통 떡볶이",

"imageUrl": "https://cdn.example.com/menus/tteokbokki.jpg",

"createdAt": "2026-07-15T10:30:00Z"

}

}
```

### 12-3. 정산

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/merchants/me/shop/wallet` | 상인 잔액 조회 | - | 잔액 정보 | ✅ MERCHANT | - |
| POST | `/merchants/me/shop/settlements` | 정산 신청 | amount | 정산 신청 정보 | ✅ MERCHANT | 🔴 동시성(잔액 차감) |
| GET | `/merchants/me/shop/settlements` | 정산 내역 조회 | cursor, size | 정산 내역 리스트 | ✅ MERCHANT | Cursor 페이징 |

**GET `/merchants/me/shop/wallet`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"shopId": 201,

"balance": 320000,

"pendingSettlement": 50000,

"totalEarned": 1500000,

"lastUpdatedAt": "2026-07-15T13:01:00Z"

}

}
```

**POST `/merchants/me/shop/settlements`**

Request

```json
{

"amount": 200000

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"settlementId": 30,

"amount": 200000,

"remainingBalance": 120000,

"bankCode": "088",

"accountNumber": "****5678",

"status": "PENDING",

"estimatedDate": "2026-07-18",

"createdAt": "2026-07-15T15:00:00Z"

}

}
```

---

## 💬 13. 채팅 / 알림 / 번역 / AI FAQ / 동행리뷰 / 고객센터

### 13-1. 동행 채팅 (Chat Room)

> 메시지는 MySQL에 저장하지만, 영구 보관을 전제로 하지 않는다. MVP에서는 최근 메시지 조회 중심으로 저장하며, 보관 기간/삭제 정책은 추후 운영 정책으로 분리한다.
> 

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/chat-rooms` | 채팅방 생성 | postId | 생성된 채팅방 | ✅ USER | 동행 게시글 기반 |
| GET | `/chat-rooms` | 내 채팅방 목록 | cursor, size | 채팅방 리스트 | ✅ USER | Cursor 페이징 |
| GET | `/chat-rooms/{chatRoomId}` | 채팅방 상세 | - | 채팅방 상세 | ✅ USER (참여자) | - |
| GET | `/chat-rooms/{chatRoomId}/messages` | 메시지 목록 | cursor, size | 메시지 리스트 | ✅ USER (참여자) | Cursor 페이징 |
| POST | `/chat-rooms/{chatRoomId}/files` | 채팅 파일/이미지 업로드 | multipart/form-data(file) | fileUrl, fileName, fileSize, contentType | ✅ USER (참여자) | S3 업로드 후 메시지 전송 시 URL 사용 |

### 예시 JSON

**POST `/chat-rooms`**

Request

```json
{

"postId": 301

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"chatRoomId": 70,

"postId": 301,

"title": "7/20 경복궁 같이 가실 분!",

"ownerId": 1001,

"maxMembers": 4,

"currentMembers": 1,

"status": "OPEN",

"createdAt": "2026-07-15T10:05:00Z"

}

}
```

**GET `/chat-rooms?cursor=eyJpZCI6NzB9&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "chatRoomId": 70,

    "title": "7/20 경복궁 같이 가실 분!",

    "currentMembers": 3,

    "maxMembers": 4,

    "lastMessage": {

      "content": "내일 10시에 3번 출구에서 만나요!",

      "senderNickname": "한국여행자",

      "sentAt": "2026-07-15T18:00:00Z"

    },

    "unreadCount": 2,

    "status": "OPEN"

  }

],

"nextCursor": "eyJpZCI6NjV9",

"hasNext": true,

"size": 10

}

}
```

**POST `/chat-rooms/70/files`**

채팅방 파일/이미지 메시지는 먼저 S3에 업로드한 뒤, 반환된 파일 URL을 WebSocket 메시지에 포함해 전송한다.

file: image_001.jpg

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "파일 업로드가 완료되었습니다.",
  "data": {
    "fileUrl": "https://cdn.example.com/chat/70/image_001.jpg",
    "fileName": "image_001.jpg",
    "fileSize": 245000,
    "contentType": "image/jpeg"
  }
}
```

**파일/이미지 메시지 Payload 예시**

```json
{
  "chatRoomId": 70,
  "messageType": "IMAGE",
  "content": "광장시장 입구 사진이에요.",
  "fileUrl": "https://cdn.example.com/chat/70/image_001.jpg",
  "fileName": "image_001.jpg",
  "fileSize": 245000
}
```

응답 필드는 DB 컬럼명과 분리하여 `messageType`, `sentAt`을 사용한다.

**GET `/chat-rooms/70/messages?cursor=eyJpZCI6NTAwfQ==&size=30`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "messageId": 499,

    "chatRoomId": 70,

    "senderId": 1001,

    "senderNickname": "한국여행자",

    "senderProfileImageUrl": "https://cdn.example.com/profiles/1001.jpg",

    "content": "내일 10시에 3번 출구에서 만나요!",

    "messageType": "TEXT",

    "sentAt": "2026-07-15T18:00:00Z"

  }

],

"nextCursor": "eyJpZCI6NDk5fQ==",

"hasNext": true,

"size": 30

}

}
```

### 13-1b. 채팅 참여 신청

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/chat-rooms/{chatRoomId}/join-requests` | 참여 신청 | message? | 신청 정보 | ✅ USER | - |
| GET | `/chat-rooms/{chatRoomId}/join-requests` | 신청 목록 조회 | - | 신청 리스트 | ✅ USER (방장) | - |
| POST | `/chat-rooms/{chatRoomId}/join-requests/{joinRequestId}/approve` | 신청 수락 | - | 수락 결과 | ✅ USER (방장) | - |
| POST | `/chat-rooms/{chatRoomId}/join-requests/{joinRequestId}/reject` | 신청 거절 | reason? | 거절 결과 | ✅ USER (방장) | - |
| DELETE | `/chat-rooms/{chatRoomId}/join-requests/{joinRequestId}` | 신청 취소 | - | - | ✅ USER (신청자) | - |
- 신청자 상세 프로필 조회는 User 도메인의 API를 사용한다. 
[ GET /api/v1/users/{userId}/companion-profile ]

**POST `/chat-rooms/70/join-requests`**

Request

```json
{

"message": "저도 경복궁 가고 싶었는데 같이 가요!"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"joinRequestId": 120,

"chatRoomId": 70,

"userId": 1003,

"nickname": "여행초보",

"profileImageUrl": "https://cdn.example.com/profiles/1003.jpg",

"companionScore": 4.0,

"message": "저도 경복궁 가고 싶었는데 같이 가요!",

"status": "PENDING",

"createdAt": "2026-07-15T11:30:00Z"

}

}
```

**POST `/chat-rooms/70/join-requests/120/approve`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"joinRequestId": 120,

"status": "APPROVED",

"chatRoomId": 70,

"currentMembers": 3

}

}
```

### 13-1c. 채팅방 참여자 관리

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/chat-rooms/{chatRoomId}/members` | 참여자 목록 | - | 참여자 리스트 | ✅ USER (참여자) | - |
| DELETE | `/chat-rooms/{chatRoomId}/members/{targetUserId}` | 참여자 강퇴 | reason? | - | ✅ USER (방장) | - |

**GET `/chat-rooms/70/members`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": [

{

  "userId": 1001,

  "nickname": "한국여행자",

  "profileImageUrl": "https://cdn.example.com/profiles/1001.jpg",

  "companionScore": 4.3,

  "memberState": "OWNER_ACTIVE",

  "joinedAt": "2026-07-15T10:05:00Z"

},

{

  "userId": 1003,

  "nickname": "여행초보",

  "profileImageUrl": "https://cdn.example.com/profiles/1003.jpg",

  "companionScore": 4.0,

  "memberState": "MEMBER_ACTIVE",

  "joinedAt": "2026-07-15T11:35:00Z"

}

]

}
```

### 13-1d. WebSocket (STOMP)

| 유형 | 경로 | 설명 | 비고 |
| --- | --- | --- | --- |
| CONNECT | `/ws-stomp` | WebSocket 연결 | JWT 인증 (handshake) |
| PUB | `/pub/chat/rooms/{chatRoomId}/messages` | 메시지 발행 | - |
| SUB | `/sub/chat/rooms/{chatRoomId}` | 메시지 구독 | - |

**PUB 메시지 포맷**

```json
{

"content": "안녕하세요! 내일 경복궁 앞에서 만나요.",

"messageType": "TEXT"

}
```

**SUB 수신 메시지 포맷**

```json
{

"messageId": 500,

"chatRoomId": 70,

"senderId": 1001,

"senderNickname": "한국여행자",

"senderProfileImageUrl": "https://cdn.example.com/profiles/1001.jpg",

"content": "안녕하세요! 내일 경복궁 앞에서 만나요.",

"messageType": "TEXT",

"sentAt": "2026-07-15T19:00:00Z"

}
```

---

### 13-2. 알림 (Notification)

> Notification 도메인은 확장 가능한 공통 알림 구조로 설계한다. MVP에서는 동행 채팅 알림과 고객센터 상담 알림을 우선 구현한다.
> 

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/notifications` | 알림 목록 조회 | cursor, size | 알림 리스트 | ✅ USER | Cursor 페이징 |
| PATCH | `/notifications/{notificationId}/read` | 알림 읽음 처리 | - | - | ✅ USER | - |
| PATCH | `/notifications/read-all` | 전체 알림 읽음 처리 | - | - | ✅ USER | MVP는 채팅방 관련 알림 대상 |
| DELETE | `/notifications/{notificationId}` | 알림 삭제 | - | - | ✅ USER | Soft Delete |

### 알림 타입

| type | 설명 | referenceType |
| --- | --- | --- |
| CHAT_MESSAGE | 동행 채팅 새 메시지 알림 | CHAT_ROOM |
| CHAT_JOIN_REQUEST | 채팅방 참여 신청 알림 | JOIN_REQUEST |
| CHAT_JOIN_APPROVED | 참여 신청 승인 알림 | CHAT_ROOM |
| CHAT_JOIN_REJECTED | 참여 신청 거절 알림 | CHAT_ROOM |
| CHAT_MEMBER_KICKED | 채팅방 강퇴 알림 | CHAT_ROOM |
| SUPPORT_MESSAGE | 고객센터 상담 새 메시지 알림 | SUPPORT_ROOM |
| SUPPORT_ROOM_CLOSED | 고객센터 상담 종료 알림 | SUPPORT_ROOM |

| 유형 | 경로 | 설명 |
| --- | --- | --- |
| SUB | `/user/queue/notifications` | 실시간 개인 알림 구독 |

**GET `/notifications?cursor=eyJpZCI6MTAwfQ==&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "notificationId": 99,

    "type": "CHAT_MESSAGE",

    "title": "새 채팅 메시지",

    "message": "한국여행자님이 메시지를 보냈습니다.",

    "referenceId": 70,

    "referenceType": "CHAT_ROOM",

    "isRead": false,

    "createdAt": "2026-07-15T19:00:00Z"

  },

  {

    "notificationId": 98,

    "type": "CHAT_JOIN_APPROVED",

    "title": "참여 신청 수락",

    "message": "\"7/20 경복궁 같이 가실 분!\" 채팅방 참여가 승인되었습니다.",

    "referenceId": 70,

    "referenceType": "CHAT_ROOM",

    "isRead": true,

    "createdAt": "2026-07-15T11:35:00Z"

  }

],

"nextCursor": "eyJpZCI6OTh9",

"hasNext": true,

"size": 10

}

}
```

**SUB 실시간 알림 포맷**

```json
{

"notificationId": 101,

"type": "CHAT_MESSAGE",

"title": "새 채팅 메시지",

"message": "여행초보님이 메시지를 보냈습니다.",

"referenceId": 70,

"referenceType": "CHAT_ROOM",

"createdAt": "2026-07-15T19:30:00Z"

}
```

---

### 13-3. 번역 (Translation)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/translations` | 채팅방 메시지 문장 번역 | content, targetLanguage | 번역 결과 | ✅ USER | Google Cloud Translation 연동, 결제 화면 번역은 PG사 기능 사용, 실패 시 COMMON_007 |

> 번역 실패 시 클라이언트 에러 코드는 `COMMON_007`로 통일하고, 상세 실패 내용은 `common_error_logs`에 기록한다.
> 

**POST `/translations`**

Request

```json
{

"content": "내일 몇 시에 만날까요?",

"targetLanguage": "en"

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"originalText": "내일 몇 시에 만날까요?",

"translatedText": "What time shall we meet tomorrow?",

"targetLanguage": "en"

}

}
```

---

### 13-4. AI FAQ

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/ai/faq-chat` | AI FAQ 질문 | question, sessionId? | 답변 | ✅ USER | 룰베이스 → FAQ DB → AI 순 |

**POST `/ai/faq-chat`**

Request

```json
{

"question": "엽전은 어떻게 충전하나요?",

"sessionId": "session-abc-123"

}
```

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"answer": "엽전은 마이페이지 > 엽전 충전 메뉴에서 카드 결제를 통해 충전하실 수 있습니다. 최소 충전 금액은 5,000원입니다.",

"source": "FAQ_DB",

"sessionId": "session-abc-123",

"relatedQuestions": [

  "엽전 환불은 어떻게 하나요?",

  "엽전으로 뭘 살 수 있나요?"

]

}

}
```

---

### 13-5. 동행 리뷰 / 점수

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/companion-reviews` | 동행 리뷰 작성 | chatRoomId, targetUserId, rating, content | 생성된 리뷰 | ✅ USER | 같은 채팅방 참여자만 |
| GET | `/users/{userId}/companion-score` | 동행 점수 조회 | - | 평균 점수, 리뷰 수 | ❌ | 🟢 캐싱(TTL 10분) |

**POST `/companion-reviews`**

Request

```json
{

"chatRoomId": 70,

"targetUserId": 1003,

"score": 5,

"content": "시간 약속을 잘 지키고 함께 여행하기 즐거웠습니다!"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"reviewId": 200,

"chatRoomId": 70,

"writerUserId": 1001,

"targetUserId": 1003,

"score": 5,

"content": "시간 약속을 잘 지키고 함께 여행하기 즐거웠습니다!",

"createdAt": "2026-07-21T20:00:00Z"

}

}
```

**GET `/users/1003/companion-score`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"userId": 1003,

"nickname": "여행초보",

"averageScore": 4.5,

"reviewCount": 8,

"scoreDistribution": {

  "5": 4,

  "4": 3,

  "3": 1,

  "2": 0,

  "1": 0

}

}

}
```

---

### 13-6. 고객센터 채팅 (Support)

> 고객센터 채팅은 AI FAQ와 함께 MVP에 포함한다. 동행 채팅과 동일한 `/ws-stomp` 연결을 사용할 수 있지만 STOMP 발행/구독 경로는 `/support`로 분리한다.
> 

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/support/rooms` | 상담방 생성 | initialMessage? | 상담방 정보 | ✅ USER | MVP 포함 |
| GET | `/support/rooms/me` | 내 상담방 목록 조회 | status?, cursor, size | 상담방 리스트 | ✅ USER | Cursor 페이징 |
| GET | `/support/rooms/{supportRoomId}/messages` | 상담 메시지 조회 | cursor, size | 메시지 리스트 | ✅ USER/ADMIN | 상담 참여자 또는 관리자 |
| GET | `/admin/support/rooms` | 상담방 목록 조회 | status?, cursor, size | 상담방 리스트 | ✅ ADMIN | Cursor 페이징 |
| POST | `/admin/support/rooms/{supportRoomId}/close` | 상담 종료 | summary? | 종료 결과 | ✅ ADMIN | - |

| 유형 | 경로 | 설명 |
| --- | --- | --- |
| CONNECT | `/ws-stomp` | WebSocket 연결 |
| PUB | `/pub/support/rooms/{supportRoomId}/messages` | 고객센터 메시지 발행 |
| SUB | `/sub/support/rooms/{supportRoomId}` | 고객센터 메시지 구독 |

**GET `/admin/support/rooms?status=WAITING&size=10`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "supportRoomId": 30,

    "userId": 1005,

    "userNickname": "제주사랑",

    "status": "WAITING",

    "lastMessage": {

      "content": "결제가 안 돼요ㅠㅠ",

      "sentAt": "2026-07-15T16:00:00Z"

    },

    "createdAt": "2026-07-15T15:55:00Z"

  }

],

"nextCursor": "eyJpZCI6MzB9",

"hasNext": false,

"size": 10

}

}
```

---

## 🏢 14. 관리자 (Admin)

### 14-1. 사용자 관리

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/users` | 유저 목록 조회 | keyword?, status?, cursor, size | 유저 리스트 | ✅ ADMIN | Cursor 페이징 |
| GET | `/admin/users/{userId}` | 유저 상세 조회 | - | 유저 상세 | ✅ ADMIN | - |
| POST | `/admin/users/{userId}/suspensions` | 계정 정지 | reason, durationDays | 정지 정보 | ✅ ADMIN | - |
| DELETE | `/admin/users/{userId}/suspensions` | 정지 해제 | - | - | ✅ ADMIN | - |

**GET `/admin/users?keyword=여행&status=ACTIVE&cursor=eyJpZCI6MTAwfQ==&size=20`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"content": [

  {

    "userId": 1001,

    "email": "traveler@example.com",

    "nickname": "한국여행자",

    "status": "ACTIVE",

    "role": "USER",

    "reportCount": 0,

    "createdAt": "2026-01-10T12:00:00Z"

  }

],

"nextCursor": "eyJpZCI6OTl9",

"hasNext": true,

"size": 20

}

}
```

**POST `/admin/users/1005/suspensions`**

Request

```json
{

"reason": "반복적인 스팸 게시글 작성",

"durationDays": 30

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"userId": 1005,

"status": "SUSPENDED",

"reason": "반복적인 스팸 게시글 작성",

"suspendedUntil": "2026-08-14T00:00:00Z",

"suspendedAt": "2026-07-15T16:00:00Z"

}

}
```

### 14-1a. 가게 관리 (Admin Shop)

상인 본인용 API와 관리자 관리용 API는 분리한다. 상인은 자신의 가게만 조회/수정하고, 관리자는 전용 admin API를 통해 전체 가게를 관리한다.

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/shops/{shopId}` | 관리자용 가게 상세 조회 | - | 가게 상세 | ✅ ADMIN | 전체 가게 관리 |
| PATCH | `/admin/shops/{shopId}` | 관리자용 가게 정보 수정 | status?, description?, phone?, operatingHours? | 수정된 가게 | ✅ ADMIN | 전체 가게 관리 |

### 14-2. 상인 관리

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/merchant-applications` | 상인 신청 목록 | status?, cursor, size | 신청 리스트 | ✅ ADMIN | Cursor 페이징 |
| GET | `/admin/merchant-applications/{applicationId}` | 신청 상세 | - | 신청 상세 | ✅ ADMIN | - |
| POST | `/admin/merchant-applications/{applicationId}/approve` | 신청 승인 | - | 승인 결과 | ✅ ADMIN | - |
| POST | `/admin/merchant-applications/{applicationId}/reject` | 신청 거절 | rejectReason | 거절 결과 | ✅ ADMIN | - |
| POST | `/admin/merchants/{merchantId}/suspensions` | 상인 정지 | reason | 정지 정보 | ✅ ADMIN | - |

**POST `/admin/merchant-applications/50/approve`**

Response 200

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"applicationId": 50,

"shopId": 201,

"shopName": "광화문 떡볶이",

"status": "APPROVED",

"approvedAt": "2026-07-15T17:00:00Z"

}

}
```

### 14-3. AI FAQ 관리

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/faqs` | FAQ 목록 조회 | cursor, size | FAQ 리스트 | ✅ ADMIN | Cursor 페이징 |
| POST | `/admin/faqs` | FAQ 등록 | question, answer, category | 생성된 FAQ | ✅ ADMIN | - |
| PUT | `/admin/faqs/{faqId}` | FAQ 수정 | question, answer, category | 수정된 FAQ | ✅ ADMIN | - |
| DELETE | `/admin/faqs/{faqId}` | FAQ 삭제 | - | - | ✅ ADMIN | - |

**POST `/admin/faqs`**

Request

```json
{

"question": "엽전은 어떻게 충전하나요?",

"answer": "마이페이지 > 엽전 충전에서 카드 결제로 충전 가능합니다. 최소 5,000원부터 충전됩니다.",

"category": "PAYMENT"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"faqId": 15,

"question": "엽전은 어떻게 충전하나요?",

"answer": "마이페이지 > 엽전 충전에서 카드 결제로 충전 가능합니다. 최소 5,000원부터 충전됩니다.",

"category": "PAYMENT",

"createdAt": "2026-07-15T10:00:00Z"

}

}
```

### 14-4. 광고 / 인증 가게

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/ad-applications` | 광고 신청 목록 | status?, cursor, size | 신청 리스트 | ✅ ADMIN | Cursor 페이징 |
| GET | `/admin/ad-applications/{applicationId}` | 광고 신청 상세 | - | 신청 상세 | ✅ ADMIN | - |
| POST | `/admin/ad-applications/{applicationId}/approve` | 광고 승인 | startDate, endDate | 승인 결과 | ✅ ADMIN | - |
| POST | `/admin/ad-applications/{applicationId}/reject` | 광고 거절 | rejectReason | 거절 결과 | ✅ ADMIN | - |
| POST | `/admin/ads` | 광고 직접 등록 | shopId, startDate, endDate, type | 등록 결과 | ✅ ADMIN | 대면 계약용 |
| GET | `/admin/shop-certifications` | 인증 신청 목록 | status?, cursor, size | 신청 리스트 | ✅ ADMIN | Cursor 페이징 |
| GET | `/admin/shop-certifications/{applicationId}` | 인증 신청 상세 | - | 신청 상세 | ✅ ADMIN | - |
| POST | `/admin/shop-certifications/{applicationId}/approve` | 인증 승인 | - | 승인 결과 | ✅ ADMIN | - |
| POST | `/admin/shop-certifications/{applicationId}/reject` | 인증 거절 | rejectReason | 거절 결과 | ✅ ADMIN | - |
| POST | `/admin/shops/{shopId}/certification/cancel` | 인증 취소 | reason | 취소 결과 | ✅ ADMIN | - |

**POST `/admin/ads`**

Request

```json
{

"shopId": 201,

"startDate": "2026-08-01",

"endDate": "2026-08-31",

"type": "BANNER"

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"adId": 80,

"shopId": 201,

"shopName": "광화문 떡볶이",

"type": "BANNER",

"startDate": "2026-08-01",

"endDate": "2026-08-31",

"status": "SCHEDULED",

"createdAt": "2026-07-15T17:30:00Z"

}

}
```

### 14-5. 콘텐츠 관리 (관광지, 행사, 배너)

| Method | Endpoint | 설명 | Request | Response | 인증 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/admin/places` | 관광지 목록 | keyword?, category?, cursor, size | 관광지 리스트 | ✅ ADMIN | Cursor 페이징 |
| POST | `/admin/places` | 관광지 등록 | name, category, address, lat, lng, description, imageUrls[] | 생성된 관광지 | ✅ ADMIN | - |
| PUT | `/admin/places/{placeId}` | 관광지 수정 | 전체 필드 | 수정된 관광지 | ✅ ADMIN | - |
| DELETE | `/admin/places/{placeId}` | 관광지 삭제/비공개 | - | - | ✅ ADMIN | Soft delete |
| GET | `/admin/events` | 행사 목록 | cursor, size | 행사 리스트 | ✅ ADMIN | Cursor 페이징 |
| POST | `/admin/events` | 행사 등록 | name, description, startDate, endDate, placeId? | 생성된 행사 | ✅ ADMIN | - |
| PUT | `/admin/events/{eventId}` | 행사 수정 | 전체 필드 | 수정된 행사 | ✅ ADMIN | - |
| DELETE | `/admin/events/{eventId}` | 행사 삭제 | - | - | ✅ ADMIN | - |
| GET | `/admin/banners` | 배너 목록 | cursor, size | 배너 리스트 | ✅ ADMIN | Cursor 페이징 |
| POST | `/admin/banners` | 배너 등록 | title, imageUrl, linkUrl, startDate, endDate, priority | 생성된 배너 | ✅ ADMIN | - |
| PUT | `/admin/banners/{bannerId}` | 배너 수정 | 전체 필드 | 수정된 배너 | ✅ ADMIN | - |
| DELETE | `/admin/banners/{bannerId}` | 배너 삭제 | - | - | ✅ ADMIN | - |

**POST `/admin/places`**

Request

```json
{

"name": "남산서울타워",

"category": "LANDMARK",

"address": "서울 용산구 남산공원길 105",

"latitude": 37.5512,

"longitude": 126.9882,

"description": "서울의 대표적인 랜드마크로 360도 전망을 감상할 수 있습니다.",

"operatingHours": "10:00 ~ 23:00",

"admissionFee": "16000원",

"phone": "02-3455-9277",

"imageUrls": ["https://cdn.example.com/uploads/namsan_1.jpg"]

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"placeId": 100,

"name": "남산서울타워",

"category": "LANDMARK",

"status": "ACTIVE",

"sentAt": "2026-07-15T18:00:00Z"

}

}
```

**POST `/admin/banners`**

Request

```json
{

"title": "보령머드축제 개최!",

"imageUrl": "https://cdn.example.com/uploads/banner_mud.jpg",

"linkUrl": "/festivals/8",

"startDate": "2026-07-10",

"endDate": "2026-07-27",

"priority": 1

}
```

Response 201

```json
{

"success": true,

"code": "SUCCESS",

"message": "요청이 성공적으로 처리되었습니다.",

"data": {

"bannerId": 20,

"title": "보령머드축제 개최!",

"status": "ACTIVE",

"priority": 1,

"createdAt": "2026-07-15T18:30:00Z"

}

}
```

---

## 📋 부록: 특수 표기 범례

| 표기 | 의미 |
| --- | --- |
| 🟢 캐싱 | Redis 또는 로컬 캐시 적용 대상 (TTL 명시) |
| 🔴 동시성 | 동시성 제어 필수 (비관적 락, 멱등성 키, UNIQUE 제약 등) |
| ✅ USER | 일반 사용자 JWT 인증 필요 |
| ✅ MERCHANT | 상인 JWT 인증 필요 |
| ✅ ADMIN | 관리자 JWT 인증 필요 |
| ✅ ALL | 모든 인증된 사용자 |
| ❌ | 인증 불필요 (공개 API) |

---

## 📋 부록: 공통 에러 응답

> API 명세서 본문과 동일하게 모든 에러 응답은 `success`, `code`, `message`, `data` 구조를 사용한다.
> 
> 
> 에러코드는 `12_공통_에러코드_설계서_error_code_design.md`의 `{도메인 prefix}_{세자리 숫자}` 규칙을 따른다.
> 

400 Bad Request

```json
{

"success": false,

"code": "COMMON_004",

"message": "입력값이 유효하지 않습니다.",

"data": null

}
```

401 Unauthorized

```json
{

"success": false,

"code": "AUTH_002",

"message": "액세스 토큰이 만료되었습니다.",

"data": null

}
```

403 Forbidden

```json
{

"success": false,

"code": "AUTH_007",

"message": "접근 권한이 없습니다.",

"data": null

}
```

404 Not Found

```json
{

"success": false,

"code": "PLACE_001",

"message": "존재하지 않는 관광지입니다.",

"data": null

}
```

409 Conflict

```json
{

"success": false,

"code": "USER_006",

"message": "이미 찜한 관광지입니다.",

"data": null

}
```

400 Bad Request - 잔액 부족

```json
{

"success": false,

"code": "PAY_001",

"message": "엽전 잔액이 부족합니다.",

"data": null

}
```

429 Too Many Requests

```json
{

"success": false,

"code": "COMMON_006",

"message": "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",

"data": null

}
```

---

## 📋 부록: 동시성 제어 API 상세

| API | 동시성 이슈 | 제어 방식 |
| --- | --- | --- |
| `POST /yeopjeon/qr/pay` | 잔액 이중 차감 | 비관적 락 (`SELECT ... FOR UPDATE`) |
| `POST /store/products/{id}/purchase` | 재고 초과 판매 | 비관적 락 + 엽전 잔액 락 (트랜잭션 내 순차 처리) |
| `POST /payments/charge` | 중복 결제 | 멱등성 키 (`Idempotency-Key` 헤더) |
| `POST /places/{placeId}/like` | 중복 찜 | DB UNIQUE 제약 (`user_id, place_id`) |
| `PATCH /admin/refunds/{id}/approve` | 이중 승인 | 낙관적 락 (status 상태 전이 검증) |
| `POST /yeopjeon/qr/confirm` | 이중 승인 | 낙관적 락 (status 상태 전이 검증) |
| `POST /merchants/me/shop/ads/{adId}/extend` | 엽전 이중 차감 | 비관적 락 |
| `POST /merchants/me/shop/settlements` | 잔액 이중 차감 | 비관적 락 |