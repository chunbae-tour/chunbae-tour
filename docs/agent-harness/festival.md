# Festival 하네스

이 하네스는 축제·행사 조회와 캘린더 작업 전용입니다.

관광지 기본 정보, 위치 기반 탐색, 외부 API 수집기는 별도 도메인 영향이 크므로 먼저 보고합니다.

## 담당 기능

| 포함 | 제외 |
|---|---|
| 축제 전체 조회 | 관광지 기본 정보 CRUD |
| 축제 상세 조회 | 위치 기반 탐색 |
| 날짜 범위 조회 | Redis Geospatial |
| 월별 캘린더 조회 | 외부 API 수집 배치 |
| 관리자 축제 등록·수정·삭제 후보 | 관리자 공통 구조 변경 |

## 허용 수정 범위

| 구분 | 경로 |
|---|---|
| 운영 코드 | `src/main/java/com/chunbaetour/domain/festival/**` |
| 테스트 코드 | `src/test/java/com/chunbaetour/domain/festival/**` |
| 도메인 문서 | `docs/park-kyunghwa-domain.md` |
| 하네스 문서 | `docs/agent-harness/festival.md` |

외부 API 키나 application 설정이 필요하면 직접 수정하지 말고 먼저 보고합니다.

## 기본 모델

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

상태 후보는 다음입니다.

```text
SCHEDULED
ONGOING
ENDED
HIDDEN
```

## API 후보

```text
GET /api/v1/festivals
GET /api/v1/festivals/{festivalId}
GET /api/v1/festivals/calendar?year=2026&month=5
GET /api/v1/festivals/search?areaName=서울&from=2026-05-01&to=2026-05-31
```

관리자 API 후보는 다음입니다.

```text
POST   /api/v1/admin/festivals
PATCH  /api/v1/admin/festivals/{festivalId}
DELETE /api/v1/admin/festivals/{festivalId}
```

## 권한 기준

| 기능 | 권한 |
|---|---|
| 축제 조회 | 비로그인 가능 |
| 캘린더 조회 | 비로그인 가능 |
| 축제 등록·수정·삭제 | `ADMIN` |

## 중단하고 보고할 조건

| 상황 | 이유 |
|---|---|
| 외부 축제 API 연동 필요 | 설정, 키, 배치 구조 영향 |
| 관광지 엔티티와 직접 연관 필요 | Tourism 도메인 영향 |
| 위치 좌표 저장 필요 | 지도·위치 도메인 정책 필요 |
| 관리자 권한 설정 수정 필요 | SecurityConfig 영향 |
| 축제 이미지 업로드 필요 | 파일 저장소 정책 필요 |
| 공통 에러코드 추가 필요 | `common` 변경 |

## 권장 구현 순서

1. 축제 엔티티와 상태 enum
2. Repository
3. 날짜 범위 조회 쿼리
4. 월별 캘린더 응답 DTO
5. Service
6. Controller
7. 테스트

## 캘린더 응답 기준

월별 캘린더는 화면이 바로 사용할 수 있도록 날짜별 그룹핑을 우선 고려합니다.

```text
year
month
days[]
  date
  festivals[]
```

단순 목록 응답이 필요한지 날짜별 그룹 응답이 필요한지 애매하면 먼저 질문합니다.

## 테스트 기준

| 테스트 | 기대 |
|---|---|
| 전체 조회 | 숨김 축제 제외 |
| 상세 조회 | 존재하는 축제 반환 |
| 날짜 범위 조회 | 기간이 겹치는 축제 포함 |
| 월별 캘린더 조회 | 해당 월에 노출되는 축제 반환 |
| 잘못된 날짜 범위 | 실패 |

## 완료 보고 체크

| 항목 | 필수 |
|---|---|
| 사용 하네스 | Festival |
| 외부 API 또는 설정 파일 영향 여부 | 필수 |
| 보호 파일 수정 여부 | 필수 |
| 테스트 결과 | 필수 |
