# 춘배투어 - 박경화 파트 도메인 명세서

> **용도**: Claude CLI가 읽고 구현 작업에 사용하기 위한 참조 문서  
> **담당 도메인**: 커뮤니티(게시글·댓글) / 관광지 리뷰(Place 도메인 추가 구현) / 캘린더(축제) / 신고  
> **프로젝트**: 춘배투어 (ChunBae Tour)  
> **기술 스택**: Java 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL(RDS), AWS S3  
> **Base URL**: `/api/v1`

---


---

## 4. Report 도메인 (신고)

### 4-1. 패키지 구조

> 사용자용 `ReportController`와 관리자용 `AdminReportController`를 **Report 도메인 패키지 안에서 분리**한다.  
> `AdminReportController`는 별도 Service 없이 `ReportService`를 직접 호출한다.  
> `ReportResolveRequest`, `MerchantReportResolveRequest`는 **Report 도메인 dto**에 위치한다.

```
domain/report
├── controller
│   ├── ReportController.java           ← 사용자 신고 생성 + 내 신고 내역 조회 담당
│   └── AdminReportController.java      ← 관리자 신고 조회/처리 담당
│                                          ReportService 직접 호출
├── service
│   └── ReportService.java              ← 신고 생성 + 신고 조회/처리 비즈니스 로직 모두 담당
├── repository
│   └── ReportRepository.java
├── entity
│   ├── Report.java
│   ├── ReportTargetType.java   (POST_COMPANION, POST_FREE, COMMENT, REVIEW, USER, MERCHANT)
│   ├── ReportReason.java       (SPAM, OBSCENE, ILLEGAL, HARASSMENT, MISINFORMATION, OTHER)
│   └── ReportStatus.java       (PENDING, RESOLVED, DISMISSED)
└── dto
    ├── ReportCreateRequest.java
    ├── ReportCreateResponse.java
    ├── MyReportResponse.java       ← 내 신고 내역 조회 응답 DTO
    ├── request
    │   ├── ReportResolveRequest.java          ← 콘텐츠 신고 처리 (POST_COMPANION·POST_FREE·COMMENT·REVIEW·USER)
    │   └── MerchantReportResolveRequest.java  ← 가게 신고 처리 (MERCHANT) 별도 DTO
    └── response
        └── ReportResponse.java
```

### 4-2. 신고 API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/reports` | 신고 생성 | ✅ USER |
| GET | `/reports/me` | 내가 신고한 내역 조회 (`cursor`, `size`) | ✅ USER |
| GET | `/reports/{reportId}` | 내 신고 단건 조회 (본인 신고만) | ✅ USER |
| GET | `/admin/reports` | 신고 목록 조회 | ✅ ADMIN |
| GET | `/admin/reports/{reportId}` | 신고 상세 조회 | ✅ ADMIN |
| POST | `/admin/reports/{id}/resolve` | 콘텐츠 신고 처리 (POST_COMPANION·POST_FREE·COMMENT·REVIEW·USER) | ✅ ADMIN |
| POST | `/admin/reports/{id}/resolve/merchant` | 가게 신고 처리 (MERCHANT) | ✅ ADMIN |

**POST `/reports`**

Request:
```json
{
  "targetType": "COMMENT",
  "targetId": 805,
  "reason": "SPAM",
  "description": "광고성 댓글입니다."
}
```

> `targetType`: `POST_COMPANION`, `POST_FREE`, `COMMENT`, `REVIEW`, `USER`, `MERCHANT` 중 하나  
> `reason`: `SPAM`(스팸/도배), `OBSCENE`(음란/성적), `ILLEGAL`(불법 정보), `HARASSMENT`(욕설/혐오/괴롭힘), `MISINFORMATION`(허위 정보), `OTHER`(기타) 중 하나

Response 201:
```json
{
  "code": "SUCCESS",
  "message": "OK",
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

**GET `/reports/me?cursor=eyJpZCI6MTUwfQ==&size=10`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "reportId": 150,
        "targetType": "COMMENT",
        "targetId": 805,
        "reason": "SPAM",
        "description": "광고성 댓글입니다.",
        "status": "PENDING",
        "createdAt": "2026-07-15T12:00:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6MTQ5fQ==",
    "hasNext": true,
    "size": 10
  }
}
```

**GET `/reports/{reportId}`**

> 본인이 신고한 건만 조회 가능. 타인 신고 ID로 요청 시 `AUTH_007` 반환.

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "reportId": 150,
    "targetType": "COMMENT",
    "targetId": 805,
    "reason": "SPAM",
    "description": "광고성 댓글입니다.",
    "status": "PENDING",
    "createdAt": "2026-07-15T12:00:00Z"
  }
}
```

**GET `/admin/reports?status=PENDING&cursor=eyJpZCI6MTAwfQ==&size=20`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "content": [
      {
        "reportId": 150,
        "targetType": "COMMENT",
        "targetId": 805,
        "reason": "SPAM",
        "description": "광고성 댓글입니다.",
        "status": "PENDING",
        "action": null,
        "reporterNickname": "여행자지수",
        "adminNote": null,
        "resolvedBy": null,
        "resolvedAt": null,
        "createdAt": "2026-07-15T12:00:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6MTQ5fQ==",
    "hasNext": true,
    "size": 20
  }
}
```

> `action`: PENDING 상태이면 `null`. 처리 완료 시 `WARNING`·`SUSPEND`·`DELETE`·`DISMISS`·`HIDE_SHOP`·`REVOKE_MERCHANT` 중 하나.  
> `adminNote`, `resolvedBy`, `resolvedAt`: PENDING 상태이면 `null`. 처리 완료 시 채워짐.

**GET `/admin/reports/{reportId}`**

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "reportId": 150,
    "targetType": "COMMENT",
    "targetId": 805,
    "reason": "SPAM",
    "description": "광고성 댓글입니다.",
    "status": "PENDING",
    "action": null,
    "reporterNickname": "여행자지수",
    "reporterId": 1003,
    "targetContent": "지금 바로 클릭하세요! 엄청난 혜택...",
    "adminNote": null,
    "resolvedBy": null,
    "resolvedAt": null,
    "createdAt": "2026-07-15T12:00:00Z"
  }
}
```

> 목록 응답(`ReportResponse`)에 `reporterId`·`targetContent` 추가된 확장 응답.  
> `action`·`adminNote`·`resolvedBy`·`resolvedAt`: 처리 완료 신고 조회 시 채워짐. PENDING이면 `null`.

**POST `/admin/reports/{id}/resolve`**

Request (`domain/report/dto/request/ReportResolveRequest.java`):
```json
{
  "action": "DELETE",
  "adminNote": "스팸 확인, 해당 댓글 삭제 처리"
}
```

> `action` (콘텐츠 신고): `WARNING`, `SUSPEND`, `DELETE`, `DISMISS` 중 하나

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
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

**POST `/admin/reports/{id}/resolve/merchant`**

> `targetType`이 `MERCHANT`인 신고에만 사용한다. 콘텐츠 신고에 이 엔드포인트를 사용하면 에러를 반환한다.

Request (`domain/report/dto/request/MerchantReportResolveRequest.java`):
```json
{
  "action": "HIDE_SHOP",
  "adminNote": "허위 정보 게시 확인, 가게 비공개 처리"
}
```

> `action` (가게 신고): `HIDE_SHOP`, `REVOKE_MERCHANT`, `DISMISS` 중 하나  
> - `HIDE_SHOP`: 가게 비공개 처리 (Merchant 도메인 연동 — 신현민 파트)  
> - `REVOKE_MERCHANT`: 상인 인증 취소 (Merchant 도메인 연동 — 신현민 파트)  
> - `DISMISS`: 무시, 신고 종결

Response 200:
```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "reportId": 155,
    "status": "RESOLVED",
    "action": "HIDE_SHOP",
    "adminNote": "허위 정보 게시 확인, 가게 비공개 처리",
    "resolvedAt": "2026-07-15T15:00:00Z",
    "resolvedBy": "admin01"
  }
}
```

### 4-3. 신고 처리 비즈니스 규칙

- 자기 자신 신고 불가 — `USER`, `MERCHANT` targetType에만 적용 (게시글/댓글은 ID 체계 달라 비교 무의미)
- 동일 사용자가 동일 대상 중복 신고 불가
- 신고 대상 존재·활성 상태 검증:
  - `POST_COMPANION`: companion_posts 테이블, status = ACTIVE
  - `POST_FREE`: free_posts 테이블, status = ACTIVE
  - `COMMENT`: comments 테이블 (KAN-61 merge 후 연결)
  - `USER`: accounts 테이블 (탈퇴 계정 제외)
  - `MERCHANT`: accounts 테이블, role = MERCHANT
- 신고 처리 시 `action`에 따른 후속 처리:
  - `DELETE`: 신고 대상 콘텐츠 비공개 처리 (status = `BLOCKED` 또는 `DELETED`)
  - `SUSPEND`: 작성자 계정 정지 처리 (`ReportService` → `UserService` 호출)
  - `WARNING`: 경고 기록 (MVP에서 실질 처리 없음, 상태 기록만)
  - `DISMISS`: 무시, 신고 종결
- `targetType`별 처리 흐름:
  - `POST_COMPANION`, `POST_FREE`, `COMMENT`, `REVIEW`, `USER` → `POST /admin/reports/{id}/resolve` 사용 (`ReportResolveRequest`)
  - `MERCHANT` → `POST /admin/reports/{id}/resolve/merchant` 사용 (`MerchantReportResolveRequest`)
    - 가게는 신고 접수 시 자동으로 비공개 처리하지 않는다
    - 관리자가 신고 내역을 직접 확인한 뒤 `HIDE_SHOP` 또는 `REVOKE_MERCHANT` 액션을 선택한다

### 4-4. 에러 코드 (신고 관련)

| 에러코드 enum | HTTP | code 문자열 | 메시지 | 발생 상황 | KAN |
|-------------|------|-----------|--------|----------|-----|
| `REPORT_TARGET_NOT_FOUND` | 404 | `REPORT_001` | 신고 대상을 찾을 수 없습니다. | 대상 ID 없음 또는 탈퇴 계정 | KAN-90 |
| `DUPLICATE_REPORT` | 409 | `REPORT_002` | 이미 신고한 대상입니다. | 동일 대상 중복 신고 | KAN-90 |
| `REPORT_SELF` | 400 | `REPORT_003` | 자기 자신을 신고할 수 없습니다. | USER·MERCHANT 자기신고 | KAN-90 |
| `REPORT_TARGET_INACTIVE` | 400 | `REPORT_004` | 신고할 수 없는 대상입니다. | 비활성 게시글/댓글 신고 시도 | KAN-90 |
| `REPORT_NOT_FOUND` | 404 | `REPORT_005` | 존재하지 않는 신고 내역입니다. | 신고 ID 조회 시 없을 때 | KAN-91/92 |
| `REPORT_ALREADY_RESOLVED` | 409 | `REPORT_006` | 이미 처리된 신고 내역입니다. | 중복 처리 시도 | KAN-91/92 |
| `REPORT_WRONG_ENDPOINT` | 400 | `REPORT_007` | 해당 신고 유형에 맞지 않는 처리 엔드포인트입니다. | MERCHANT 신고에 `/resolve` 또는 콘텐츠 신고에 `/resolve/merchant` 사용 시 | KAN-92 |

> KAN-90·91 브랜치 병합 시 에러코드 번호 충돌 해소 완료 (KAN-92). REPORT_001~004 사용자 신고, REPORT_005~007 관리자 처리.

### 4-5. 화면 연결

| 화면 ID | 화면명 | 관련 API |
|--------|--------|---------|
| SCR-ADMIN-003 | 신고 관리 (관리자) | `GET /admin/reports`, `GET /admin/reports/{reportId}`, `POST /admin/reports/{id}/resolve`, `POST /admin/reports/{id}/resolve/merchant` |

**SCR-ADMIN-003 신고 관리 구성**:
- 탭: [미처리 🔴N] [처리완료]
- 카드: 신고 유형(POST_COMPANION·POST_FREE·COMMENT·REVIEW·USER·MERCHANT), 신고 사유, 신고자, 날짜
- 콘텐츠 신고 액션 버튼: [상세보기] [삭제] [계정 정지] [경고] [무시]
- 가게 신고 액션 버튼: [상세보기] [가게 비공개] [상인 인증 취소] [무시]

**신고 유스케이스 (UC-62) 흐름**:
1. 관리자 대시보드 → 신고 목록 조회 → 미처리 신고 건수·목록 표시
2. 신고 건 클릭 → 신고 사유, 원문, 신고자 정보, targetType 표시
3. `targetType`에 따라 처리 방식 분기:
   - 콘텐츠 (`POST_COMPANION·POST_FREE·COMMENT·REVIEW·USER`): 삭제 / 계정 정지 / 경고 / 무시 선택
   - 가게 (`MERCHANT`): 가게 비공개 / 상인 인증 취소 / 무시 선택
4. 처리 결과 기록, 신고 건 RESOLVED 상태로 변경

---
