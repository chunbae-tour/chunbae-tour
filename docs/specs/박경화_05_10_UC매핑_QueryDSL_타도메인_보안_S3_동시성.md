# 춘배투어 - 박경화 파트 도메인 명세서

> **용도**: Claude CLI가 읽고 구현 작업에 사용하기 위한 참조 문서  
> **담당 도메인**: 커뮤니티(게시글·댓글) / 관광지 리뷰(Place 도메인 추가 구현) / 캘린더(축제) / 신고  
> **프로젝트**: 춘배투어 (ChunBae Tour)  
> **기술 스택**: Java 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL(RDS), AWS S3  
> **Base URL**: `/api/v1`

---


---

## 5. 유스케이스 매핑 요약

| UC ID | 유스케이스명 | 관련 API | 담당 도메인 |
|-------|------------|---------|------------|
| UC-16 | 축제 캘린더 조회 | `GET /festivals`, `GET /festivals/{festivalId}`, `GET /calendar`, `GET /calendar/daily` | festival |
| UC-20 | 게시글 작성 | `POST /posts/companions`, `POST /posts/free` | community |
| UC-21 | 게시글 조회 | `GET /posts/companions`, `GET /posts/companions/{id}`, `GET /posts/free`, `GET /posts/free/{id}` | community |
| UC-22 | 댓글 작성 | `POST /comments` | community |
| UC-23 | 관광지 리뷰 작성 | `POST /places/{placeId}/reviews` | place |
| UC-24 | 신고하기 | `POST /reports`, `GET /reports/me` | report |
| UC-62 | 신고 처리 (관리자) | `GET /admin/reports`, `GET /admin/reports/{reportId}`, `POST /admin/reports/{id}/resolve`, `POST /admin/reports/{id}/resolve/merchant` | report |
| UC-63 | 관광지/축제 관리 (관리자) | `GET /admin/festivals`, `POST /admin/festivals`, `PUT /admin/festivals/{id}`, `DELETE /admin/festivals/{id}` | festival (AdminFestivalController) |

---

## 6. QueryDSL 사용 대상

| Repository | 목적 |
|-----------|------|
| `PostQueryRepository` | 게시글 목록 조건 검색 (지역, 날짜, 상태 필터) |
| `ReviewQueryRepository` | 리뷰 목록 정렬/페이징 (최신순, 별점순) ← `domain/place` 내 위치 (박경화 추가 구현) |
| `FestivalQueryRepository` | 지역/날짜 필터 검색 |

---

## 7. 타 도메인 연결 포인트

| 연결 대상 | 연결 이유 | 방식 |
|---------|---------|------|
| Place 도메인 (김인목) | 동행 게시글의 `placeId` 유효성 검증 | PostService → PlaceService 호출하여 동행 게시글 생성·수정 시 placeId 존재 여부 확인 |
| Chat 도메인 (임하은) | 동행 게시글 상세에서 채팅방 참여 신청 버튼 | 게시글 상세 응답에 `chatRoomId` 포함, 프론트엔드가 Chat API 호출 |
| Admin 도메인 (정민교) | SecurityConfig `/admin/**` 권한 설정 | `AdminReportController`, `AdminFestivalController` 모두 Report/Festival 도메인 패키지 내 위치. Admin 도메인은 SecurityConfig 관리만 담당 |
| User 도메인 (정민교) | 신고 처리 시 계정 정지 (`SUSPEND` action) | `ReportService` → `UserService` 호출하여 status 변경 |
| Merchant 도메인 (신현민) | 가게 신고 처리 시 가게 비공개·상인 인증 취소 | `ReportService` → `MerchantService` 호출 (`HIDE_SHOP`: 가게 비공개, `REVOKE_MERCHANT`: 상인 인증 취소) |

---

## 8. 보안 / Rate Limit 정책

파일 업로드에 대한 Rate Limit만 박경화 파트에서 직접 적용된다.  
나머지 공통 Rate Limit 정책 (로그인, 회원가입, 검색, 채팅 메시지 등)은 `11_운영_보안_정책_설계서` 참조.

| 대상 API | 제한 기준 | 제한 횟수 | 초과 시 에러 |
|---------|---------|---------|------------|
| 파일 업로드 | userId | 10회 / 1분 | `COMMON_006` |

---

## 9. 이미지 파일 업로드

이미지는 서버(`global.infra.s3.S3Uploader`)를 통해 AWS S3에 업로드된다.

1. 클라이언트가 이미지 파일을 서버에 전송
2. 서버는 `S3Uploader`를 통해 S3에 업로드하고 URL 반환
3. 클라이언트는 반환된 S3 URL을 `imageUrls[]`에 담아 게시글 API 요청, 리뷰의 경우 `imageUrls[]` 또는 `receiptImageUrl`에 담아 Place 도메인 리뷰 API 요청

---

## 10. 좋아요 / 조회수 동시성 처리 전략

> **출처**: `09_동시성_제어_설계서` Section 3.5 — 담당: 박경화, 위험도: 🟢 낮음

### 10-1. 관광지 조회수 (MVP 반영)

> `GET /places/{placeId}` 호출 시 조회수 카운트 증가는 **김인목 파트 PlaceService**에서 처리한다.  
> 박경화 파트는 Redis에 쌓인 카운트를 DB에 동기화하는 **배치 스케줄러만 담당**한다.

```
[김인목 파트 담당]
GET /places/{placeId} 호출 시
    └─ Redis INCR "place:view:{placeId}"   ← PlaceService 내부에서 실행

[박경화 파트 담당 - 배치 스케줄러, 5분 주기]
    └─ Redis "place:view:*" 카운트 → DB UPDATE (벌크 처리)
```

### 10-2. 게시글 조회수 (MVP 반영)

게시글 조회수도 동일 패턴. `GET /posts/free/{id}` 또는 `GET /posts/companions/{id}` 호출 시 카운트.

```
Redis INCR "post:view:{postId}"   ← 게시글 상세 GET 시 실행
[배치 스케줄러 - 5분 주기] Redis → DB UPDATE 벌크 처리
```

### 10-3. 게시글 좋아요 (구현 전 결정 필요)

> ⚠️ **MVP 포함 여부 및 좋아요 취소 기능 제공 여부를 먼저 팀에서 결정해야 한다.**  
> 결정에 따라 구현 방식이 달라진다.

| 시나리오 | 구현 방식 |
|---------|---------|
| 좋아요만 (취소 없음) | `SETNX + INCR` |
| 좋아요 + 취소 | `SETNX + INCR` + `DEL + DECR` — 또는 원자성 보장을 위해 **Lua 스크립트**로 묶기 |

**좋아요만 있는 경우 (SETNX + INCR)**:

```
좋아요 요청 (POST /posts/{id}/like)
    │
    ├─ Redis SETNX "post:liked:{postId}:user:{userId}" = 1
    │   이미 존재 → "이미 좋아요를 눌렀습니다" (중복 방지)
    │
    └─ Redis INCR "post:like:{postId}"

[배치 스케줄러 - 5분 주기] Redis → DB UPDATE 벌크 처리
```

**좋아요 + 취소가 있는 경우 (Lua 스크립트 권장)**:

```lua
-- like.lua (좋아요)
local liked_key = KEYS[1]   -- "post:liked:{postId}:user:{userId}"
local count_key = KEYS[2]   -- "post:like:{postId}"
if redis.call('SETNX', liked_key, 1) == 1 then
    return redis.call('INCR', count_key)
else
    return -1  -- 이미 좋아요 상태
end

-- unlike.lua (좋아요 취소)
local liked_key = KEYS[1]
local count_key = KEYS[2]
if redis.call('DEL', liked_key) == 1 then
    return redis.call('DECR', count_key)
else
    return -1  -- 좋아요 상태 아님
end
```

> Lua 스크립트로 묶으면 SETNX → INCR 사이 장애 틈(중간 실패)을 원자적으로 방지할 수 있다.

**DB 동기화 방식 결정 사항**:

| 질문 | 선택지 | 영향 |
|------|--------|------|
| DB에 좋아요 이력을 남길 것인가? | 이력 테이블 (`PostLike`) | 재로그인 후 좋아요 상태 복원 가능 |
| 카운트만 저장할 것인가? | `posts.like_count` 컬럼만 UPDATE | 구현 단순, 이력 조회 불가 |

### 10-4. 추천 구현 순서

| 순서 | 작업 | 이유 |
|------|------|------|
| 1 | 게시글 좋아요 Redis 키 정책 확정 | `post:like:{postId}`, `post:liked:{postId}:user:{userId}` |
| 2 | SETNX + INCR 좋아요 서비스 구현 | 중복 좋아요 방지 |
| 3 | 관광지·게시글 조회수 DB 동기화 스케줄러 구현 | Redis 증가 코드 이후 마무리 필요 |
| 4 | 좋아요 DB 동기화 방식 결정 | DB에 이력 남길지, 카운트만 저장할지 결정 후 배치 구현 |
| 5 | 가능하면 Lua 스크립트로 묶기 | 동시성·장애 틈 최소화 |

---

