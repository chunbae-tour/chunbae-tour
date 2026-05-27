# 춘배투어 - 박경화 파트 도메인 명세서

> **용도**: Claude CLI가 읽고 구현 작업에 사용하기 위한 참조 문서  
> **담당 도메인**: 커뮤니티(게시글·댓글) / 관광지 리뷰(Place 도메인 추가 구현) / 캘린더(축제) / 신고  
> **프로젝트**: 춘배투어 (ChunBae Tour)  
> **기술 스택**: Java 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL(RDS), AWS S3  
> **Base URL**: `/api/v1`

---


---

## 3. Festival 도메인 (축제 · 캘린더)

### 3-1. 패키지 구조

```
domain/festival
├── controller
│   ├── FestivalController.java         ← 사용자용 GET 전용
│   ├── AdminFestivalController.java    ← 관리자용 GET/POST/PUT/DELETE 전용 (@PreAuthorize("hasRole('ADMIN')"))
│   └── CalendarController.java         ← 사용자용 GET 전용
├── service
│   ├── FestivalService.java
│   └── CalendarService.java
├── repository
│   ├── FestivalRepository.java
│   └── FestivalQueryRepository.java   ← QueryDSL (지역/날짜 필터)
├── entity
│   └── Festival.java
├── dto
│   ├── request
│   │   ├── FestivalSearchRequest.java  ← 사용자 검색 조건 (조회용)
│   │   ├── FestivalCreateRequest.java  ← 관리자 축제 등록. AdminFestivalController에서 사용
│   │   └── FestivalUpdateRequest.java  ← 관리자 축제 수정. AdminFestivalController에서 사용
│   └── response
│       ├── FestivalResponse.java
│       ├── CalendarResponse.java       ← 월별 캘린더 응답 (날짜별 Map 구조)
│       └── DailyCalendarResponse.java  ← 일별 캘린더 응답 (단일 date + events 배열)
└── type
    ├── FestivalStatus.java             (ACTIVE, HIDDEN, DELETED)
    └── FestivalProgressStatus.java     (UPCOMING, IN_PROGRESS, ENDED)

※ 사용자용 API는 FestivalController, 관리자용 API는 AdminFestivalController로 분리한다.
   AdminFestivalController의 모든 메서드에 @PreAuthorize("hasRole('ADMIN')")를 클래스 레벨로 적용한다.
```

### 3-2. 역할 분리

축제 데이터는 **관리자가 직접 등록/수정/삭제**하여 캘린더를 관리하는 구조다.  
사용자는 조회(GET)만 가능하다.

| 주체 | 가능한 작업 | 사용 API |
|------|-----------|---------|
| 사용자 (❌ 인증 불필요) | 축제 목록 조회, 단건 조회, 월별·일별 캘린더 조회 | `GET /festivals`, `GET /festivals/{id}`, `GET /calendar`, `GET /calendar/daily` |
| 관리자 (✅ ADMIN) | 축제 전체 조회(HIDDEN 포함), 등록, 수정, 삭제 | `GET /admin/festivals`, `POST /admin/festivals`, `PUT /admin/festivals/{id}`, `DELETE /admin/festivals/{id}` |

> 사용자용 API는 `FestivalController`, 관리자용 API는 `AdminFestivalController`로 분리한다.  
> 두 Controller 모두 Festival 도메인 패키지 안에 위치하며 `FestivalService`를 직접 호출한다.
>
> **권한 제어 — 2중 방어 구조**
> - **1차 (SecurityConfig)**: `/api/v1/admin/**` 경로 전체에 `.hasRole("ADMIN")` 적용
> - **2차 (@PreAuthorize 클래스 레벨)**: `AdminFestivalController`에 `@PreAuthorize("hasRole('ADMIN')")`를 클래스 레벨로 선언. 모든 메서드에 자동 적용
>
> ```java
> // FestivalController.java — 사용자 전용
> @RestController
> public class FestivalController {
>     @GetMapping("/api/v1/festivals")
>     public ApiResponse<?> getFestivals(...) { ... }
> }
>
> // AdminFestivalController.java — 관리자 전용
> @RestController
> @PreAuthorize("hasRole('ADMIN')")  // 클래스 레벨 — 모든 메서드 자동 보호
> public class AdminFestivalController {
>     @GetMapping("/api/v1/admin/festivals")
>     public ApiResponse<?> getAdminFestivals(...) { ... }
>
>     @PostMapping("/api/v1/admin/festivals")
>     public ApiResponse<?> createFestival(...) { ... }
> }
> ```

### 3-3. 사용자용 축제 / 캘린더 API

> **status** = 관리자 운영 상태: `ACTIVE / HIDDEN / DELETED`  
> **progressStatus** = 시작일/종료일 기준 계산값: `UPCOMING / IN_PROGRESS / ENDED` (DB 저장 X, 응답에서 계산)  
> ⚠️ 사용자용 API (`GET /festivals`, `GET /calendar`, `GET /calendar/daily`)는 **`status = ACTIVE`인 축제만 반환**한다. HIDDEN/DELETED는 관리자 전용 `GET /admin/festivals`에서만 조회된다.

> **🟢 Redis 캐싱 도입 이유**  
> 축제/캘린더 데이터는 변경 빈도가 낮고(관리자만 수정) 읽기 빈도가 매우 높다.  
> 매 요청마다 DB 조회하면 불필요한 부하가 발생하므로 Redis에 TTL 1시간으로 캐싱한다.  
> 관리자가 축제를 등록/수정/삭제할 때 해당 캐시 키를 명시적으로 무효화(evict)한다.
>
> **대안 기술 비교**
>
> | 방식 | 특징 | 선택하지 않은 이유 |
> |------|------|---------------|
> | **Spring Cache + Redis** (현재 선택) | `@Cacheable`/`@CacheEvict` 어노테이션으로 간단 구현, 분산 환경 지원 | — |
> | Local Cache (Caffeine/Guava) | JVM 메모리 사용, 네트워크 없음, 빠름 | 다중 서버 확장 시 서버 간 캐시 불일치 발생 |
> | DB 쿼리 최적화만 | 인덱스 + QueryDSL로 충분할 수 있음 | 트래픽 증가 시 DB 부하 직결, 확장성 떨어짐 |

| Method | Endpoint | 설명 | 인증 | 비고 |
|--------|----------|------|------|------|
| GET | `/festivals` | 축제 목록 조회 (`date?`, `region?`, `cursor?`, `size?`) | ❌ | 🟢 캐싱(TTL 1시간) |
| GET | `/festivals/{festivalId}` | 축제 단건 상세 | ❌ | 🟢 캐싱(TTL 1시간) |
| GET | `/calendar` | 월별 캘린더 조회 (`year`, `month`) | ❌ | 🟢 캐싱(TTL 1시간) |
| GET | `/calendar/daily` | 일별 축제 조회 (`date`) | ❌ | 🟢 캐싱(TTL 1시간) |

**GET `/festivals?region=서울&date=2026-07-15&size=10`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
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
        "relatedUrl": "https://festival.example.com/hangang",
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

**GET `/festivals/{festivalId}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "festivalId": 7,
    "name": "서울 한강 페스티벌",
    "description": "한강에서 즐기는 여름 축제",
    "region": "서울",
    "address": "서울 영등포구 여의동로 330",
    "startDate": "2026-07-10",
    "endDate": "2026-07-20",
    "imageUrl": "https://cdn.example.com/festivals/7.jpg",
    "relatedUrl": "https://festival.example.com/hangang",
    "status": "ACTIVE",
    "progressStatus": "IN_PROGRESS"
  }
}
```

**GET `/calendar?year=2026&month=7`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "year": 2026,
    "month": 7,
    "markedDates": ["2026-07-10", "2026-07-18"],
    "events": {
      "2026-07-10": [
        { "festivalId": 7, "name": "서울 한강 페스티벌", "address": "서울 영등포구 여의동로 330", "type": "FESTIVAL" }
      ],
      "2026-07-18": [
        { "festivalId": 8, "name": "보령머드축제", "address": "충남 보령시 대천해수욕장", "type": "FESTIVAL" },
        { "eventId": 3, "name": "전통시장 야시장", "address": "서울 종로구", "type": "EVENT" }
      ]
    }
  }
}
```

**GET `/calendar/daily?date=2026-07-18`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "date": "2026-07-18",
    "events": [
      {
        "festivalId": 8,
        "name": "보령머드축제",
        "address": "충남 보령시 대천해수욕장",
        "relatedUrl": "https://boryeongmudfestival.com",
        "startDate": "2026-07-15",
        "endDate": "2026-07-24",
        "imageUrl": "https://cdn.example.com/festivals/8.jpg",
        "type": "FESTIVAL",
        "progressStatus": "IN_PROGRESS"
      },
      {
        "eventId": 3,
        "name": "전통시장 야시장",
        "address": "서울 종로구",
        "relatedUrl": null,
        "startDate": "2026-07-18",
        "endDate": "2026-07-18",
        "imageUrl": "https://cdn.example.com/events/3.jpg",
        "type": "EVENT",
        "progressStatus": "IN_PROGRESS"
      }
    ]
  }
}
```

### 3-4. 관리자용 축제 관리 API (`domain/festival/controller/AdminFestivalController` — 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 적용)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/admin/festivals` | 축제 목록 조회 (HIDDEN 포함 전체) | ✅ ADMIN |
| POST | `/admin/festivals` | 축제 등록 | ✅ ADMIN |
| PUT | `/admin/festivals/{festivalId}` | 축제 수정 | ✅ ADMIN |
| DELETE | `/admin/festivals/{festivalId}` | 축제 삭제 | ✅ ADMIN |

**GET `/admin/festivals`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "festivalId": 7,
        "name": "서울 한강 페스티벌",
        "region": "서울",
        "address": "서울 영등포구 여의동로 330",
        "startDate": "2026-07-10",
        "endDate": "2026-07-20",
        "relatedUrl": "https://festival.example.com/hangang",
        "status": "ACTIVE",
        "progressStatus": "IN_PROGRESS"
      },
      {
        "festivalId": 9,
        "name": "비공개 축제",
        "region": "부산",
        "address": "부산 해운대구",
        "startDate": "2026-08-01",
        "endDate": "2026-08-05",
        "relatedUrl": null,
        "status": "HIDDEN",
        "progressStatus": "UPCOMING"
      }
    ],
    "nextCursor": "eyJpZCI6OX0=",
    "hasNext": false,
    "size": 20
  }
}
```

**POST `/admin/festivals`**

Request (`domain/festival/dto/request/FestivalCreateRequest.java`):
```json
{
  "name": "서울 빛초롱 축제",
  "description": "청계천 일대를 수놓는 빛의 축제",
  "region": "서울",
  "address": "서울 종로구 청계천로 일대",
  "startDate": "2026-11-01",
  "endDate": "2026-11-15",
  "imageUrl": "https://cdn.example.com/festivals/new.jpg",
  "relatedUrl": "https://seoullantern.com",
  "status": "ACTIVE"
}
```

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "festivalId": 10,
    "name": "서울 빛초롱 축제",
    "region": "서울",
    "startDate": "2026-11-01",
    "endDate": "2026-11-15",
    "status": "ACTIVE",
    "createdAt": "2026-07-15T09:00:00Z"
  }
}
```

**PUT `/admin/festivals/{festivalId}`**

Request (`domain/festival/dto/request/FestivalUpdateRequest.java`):
```json
{
  "name": "서울 빛초롱 축제 (수정)",
  "description": "청계천 일대와 광화문 광장을 수놓는 빛의 축제",
  "region": "서울",
  "address": "서울 종로구 청계천로 일대 및 광화문광장",
  "startDate": "2026-11-01",
  "endDate": "2026-11-20",
  "imageUrl": "https://cdn.example.com/festivals/10_updated.jpg",
  "relatedUrl": "https://seoullantern.com",
  "status": "ACTIVE"
}
```

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "festivalId": 10,
    "name": "서울 빛초롱 축제 (수정)",
    "region": "서울",
    "startDate": "2026-11-01",
    "endDate": "2026-11-20",
    "status": "ACTIVE",
    "updatedAt": "2026-07-16T10:00:00Z"
  }
}
```

**DELETE `/admin/festivals/{festivalId}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "축제가 삭제되었습니다."
}
```

### 3-5. 화면 연결

| 화면 ID | 화면명 | 관련 API |
|--------|--------|---------|
| SCR-FEST-001 | 축제 목록 | `GET /festivals`, `GET /festivals/{festivalId}` |
| SCR-FEST-002 | 축제 캘린더 | `GET /calendar`, `GET /calendar/daily` |
| SCR-ADMIN-FEST-001 | 관리자 축제 관리 | `GET /admin/festivals`, `POST /admin/festivals`, `PUT /admin/festivals/{festivalId}`, `DELETE /admin/festivals/{festivalId}` |

**SCR-FEST-001 축제 목록 구성**:
- 지역 필터 탭: [전체] [서울] [부산] [제주] 등
- 우측 상단: [📅 캘린더] 버튼 → SCR-FEST-002 이동
- 카드: 이미지, 축제명, 날짜 범위, 위치, 지역
- 카드 클릭 → 축제 단건 상세 (`GET /festivals/{festivalId}`)

**SCR-FEST-002 축제 캘린더 구성**:
- 월별 캘린더 표시, 좌우 화살표로 월 이동
- 축제 있는 날짜에 색상 마커 표시
- 월별 조회 Response의 `markedDates` 배열을 기반으로 해당 날짜에 색상 마커 표시
- 날짜 클릭 시 `GET /calendar/daily?date={date}` 호출 → 하단에 해당 날짜 행사 목록 표시

**SCR-ADMIN-FEST-001 관리자 축제 관리 구성**:
- 축제 목록 테이블 (HIDDEN 포함 전체, status 컬럼 포함)
- 등록 버튼 → 축제 등록 폼 (이름, 설명, 지역, 날짜, 이미지, status)
- 행 클릭 → 수정 폼 (PUT)
- 삭제 버튼 (DELETE, 확인 다이얼로그)

---

### 3-6. Festival 에러 코드

| 에러코드 | HTTP | 메시지 | 발생 상황 |
|---------|------|--------|----------|
| `FESTIVAL_001` | 404 | 존재하지 않는 축제입니다. | 축제 ID 조회 시 없을 때 |
| `FESTIVAL_002` | 403 | 삭제된 축제입니다. | 삭제 상태 축제 접근 |
| `FESTIVAL_003` | 403 | 축제 관리 권한이 없습니다. | 비관리자가 POST/PUT/DELETE 시도 시 |

**ErrorCode Enum (Festival 부분)**:
```java
FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "FESTIVAL_001", "존재하지 않는 축제입니다."),
FESTIVAL_DELETED(HttpStatus.FORBIDDEN,   "FESTIVAL_002", "삭제된 축제입니다."),
FESTIVAL_FORBIDDEN(HttpStatus.FORBIDDEN, "FESTIVAL_003", "축제 관리 권한이 없습니다."),
```

---

