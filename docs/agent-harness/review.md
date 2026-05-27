# Review 하네스

이 하네스는 관광지 리뷰와 별점 작업 전용입니다.

관광지 기본 정보 자체를 만들거나 수정하는 작업은 Tourism 도메인에 해당하므로 직접 수정하지 않습니다.

## 담당 기능

| 포함 | 제외 |
|---|---|
| 관광지별 리뷰 목록 조회 | 관광지 기본 정보 CRUD |
| 리뷰 작성 | 위치 기반 탐색 |
| 리뷰 수정 | 축제 리뷰 |
| 리뷰 삭제 또는 숨김 | 상점 리뷰 |
| 별점 검증 | 리뷰 신고 처리 |
| 내 리뷰 조회 | 관리자 공통 구조 |

## 허용 수정 범위

| 구분 | 경로 |
|---|---|
| 운영 코드 | `src/main/java/com/chunbaetour/domain/review/**` |
| 테스트 코드 | `src/test/java/com/chunbaetour/domain/review/**` |
| 도메인 문서 | `docs/park-kyunghwa-domain.md` |
| 하네스 문서 | `docs/agent-harness/review.md` |

관광지 엔티티가 없어도 임의로 Tourism 도메인을 만들지 않습니다. 우선 `touristSpotId` 참조로 설계하고, 실제 존재 검증이 필요하면 먼저 보고합니다.

## 기본 모델

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

상태 후보는 다음입니다.

```text
ACTIVE
HIDDEN
DELETED
```

## 별점 정책

MVP 기본값은 1점부터 5점까지의 정수 별점입니다.

```text
1
2
3
4
5
```

0.5 단위 별점이 필요하면 DB 타입, 검증, 평균 계산 방식이 달라지므로 먼저 보고합니다.

## API 후보

```text
GET    /api/v1/tourist-spots/{touristSpotId}/reviews
POST   /api/v1/tourist-spots/{touristSpotId}/reviews
PATCH  /api/v1/tourist-spots/{touristSpotId}/reviews/{reviewId}
DELETE /api/v1/tourist-spots/{touristSpotId}/reviews/{reviewId}
GET    /api/v1/users/me/reviews
```

## 권한 기준

| 기능 | 권한 |
|---|---|
| 리뷰 목록 조회 | 비로그인 가능 |
| 리뷰 작성 | `USER`, `MERCHANT`, `ADMIN` |
| 리뷰 수정 | 작성자 본인 또는 `ADMIN` |
| 리뷰 삭제 | 작성자 본인 또는 `ADMIN` |
| 내 리뷰 조회 | 본인 |

## 중단하고 보고할 조건

| 상황 | 이유 |
|---|---|
| 관광지 존재 여부를 실제 엔티티로 검증해야 함 | Tourism 도메인 영향 |
| 관광지 평균 별점을 관광지 테이블에 저장해야 함 | Tourism 도메인 쓰기 필요 |
| 0.5 단위 별점 필요 | DB 타입과 검증 정책 변경 |
| 리뷰 이미지 첨부 필요 | 파일 저장소 정책 필요 |
| 리뷰 신고 자동 숨김 필요 | Report와 연쇄 변경 |
| 한 관광지에 리뷰 여러 개 허용 여부가 애매함 | unique 조건 영향 |
| 공통 에러코드 추가 필요 | `common` 변경 |

## 권장 구현 순서

1. 리뷰 엔티티와 상태 enum
2. 별점 값 객체 또는 검증 메서드
3. Repository
4. 요청·응답 DTO
5. Service
6. Controller
7. 작성자 권한 검증
8. 테스트

## 테스트 기준

| 테스트 | 기대 |
|---|---|
| 리뷰 작성 성공 | 관광지 ID, 작성자 ID, 별점 저장 |
| 별점 범위 초과 | 실패 |
| 관광지별 목록 조회 | 해당 관광지 리뷰만 반환 |
| 내 리뷰 조회 | 본인 리뷰만 반환 |
| 작성자 외 수정 | 실패 |
| 작성자 외 삭제 | 실패 |
| 중복 리뷰 | 정책에 따라 실패 또는 허용 |

## 완료 보고 체크

| 항목 | 필수 |
|---|---|
| 사용 하네스 | Review |
| Tourism 도메인 영향 여부 | 필수 |
| 보호 파일 수정 여부 | 필수 |
| 테스트 결과 | 필수 |
