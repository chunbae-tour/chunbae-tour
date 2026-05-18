## Problem Statement

춘배투어 백엔드는 현재 Spring Boot 스켈레톤만 존재한다 (`ChunbaeTourApplication.java` 단일 파일). 모든 도메인이 `users` 테이블 FK를 참조하지만 `Account` 엔티티가 없어 다른 팀원들이 entity 작성을 시작할 수 없다. 또한 인증 흐름 (회원가입, 로그인, JWT 검증) 부재로 사용자 인증이 필요한 어떤 API도 구현 불가능하다.

사용자 관점에서는, 일반 사용자 / 상인 / 관리자 3개 페이지에 각각 회원가입하고 로그인해서 토큰을 받아 API를 호출할 수 있어야 한다.

## Solution

`Account` 엔티티 + Role / Status enum을 기반으로 Auth 도메인의 MVP 인증 흐름을 구현한다.

- 사용자 회원가입 1개 endpoint
- 페이지별 로그인 3개 endpoint (사용자 / 상인 / 관리자)
- 토큰 재발급 endpoint (Refresh Token Rotation)
- 로그아웃 endpoint (Refresh Token 삭제 + Access Token 블랙리스트)
- Spring Security + JWT 필터 체인
- 공통 응답/에러 처리 인프라 (AUTH_* 에러 코드)

기준 문서: `docs/account-admin-api-convention.md`, `sa-docs/04_기능_명세서` F-AUTH-001 ~ F-AUTH-003, `sa-docs/10_ADR` ADR-004 (JWT), `sa-docs/11_운영_보안_정책_설계서` 비밀번호 정책, `sa-docs/12_공통_에러코드_설계서` AUTH_001 ~ AUTH_013.

## User Stories

1. As a 신규 사용자, I want to 이메일/비밀번호/닉네임/전화번호로 회원가입할 수 있어, so that 춘배투어 서비스에 USER 계정을 만들 수 있다
2. As a 신규 사용자, I want to 회원가입 시 이메일이 이미 존재하면 명확한 에러(AUTH_008)를 받아, so that 다른 이메일로 재시도할 수 있다
3. As a 신규 사용자, I want to 회원가입 시 닉네임이 이미 존재하면 명확한 에러(AUTH_009)를 받아, so that 다른 닉네임으로 재시도할 수 있다
4. As a 신규 사용자, I want to 비밀번호 형식이 정책에 맞지 않을 때 AUTH_010 에러를 받아, so that 정책에 맞는 비밀번호로 수정할 수 있다
5. As a 신규 사용자, I want to 이메일 형식이 잘못되면 AUTH_011 에러를 받아, so that 올바른 형식으로 수정할 수 있다
6. As a 등록된 사용자, I want to `/api/v1/users/auth/login` 에서 이메일/비밀번호로 로그인할 수 있어, so that Access Token과 Refresh Token을 받을 수 있다
7. As a 상인, I want to `/api/v1/merchants/auth/login` 에서 로그인할 수 있어, so that 상인 페이지에 접근할 수 있다
8. As a 관리자, I want to `/api/v1/admin/auth/login` 에서 로그인할 수 있어, so that 관리자 페이지에 접근할 수 있다
9. As a 사용자, I want to 잘못된 비밀번호로 로그인 시도하면 AUTH_001 에러를 받아, so that 다시 시도할 수 있다
10. As a 정지된 사용자, I want to 로그인 시 AUTH_012 에러를 받아, so that 계정 정지 사실을 알 수 있다
11. As a USER 권한 계정, I want to `/api/v1/merchants/auth/login` 또는 `/api/v1/admin/auth/login` 에 접근하면 AUTH_007 에러를 받아, so that 권한 없는 페이지에 접근할 수 없다
12. As a 로그인된 사용자, I want to Access Token이 만료되면 AUTH_002 에러를 받아, so that 클라이언트가 자동으로 Refresh Token으로 재발급 요청을 보낼 수 있다
13. As a 로그인된 사용자, I want to Refresh Token이 담긴 HttpOnly Cookie로 `/api/v1/auth/reissue` 호출해서 새 Access Token을 받아, so that 재로그인 없이 세션을 이어갈 수 있다
14. As a 로그인된 사용자, I want to Refresh Token Rotation으로 매번 새 Refresh Token을 발급받아, so that 탈취된 토큰이 재사용되지 않는다
15. As a 로그인된 사용자, I want to Refresh Token이 만료되면 AUTH_004 에러를 받아, so that 다시 로그인하라는 안내를 받을 수 있다
16. As a 변조된 토큰을 가진 공격자, I want to API 호출 시 AUTH_003 (Access) 또는 AUTH_005 (Refresh) 에러를 받아, so that 시스템이 위변조를 차단한다
17. As a 로그인된 사용자, I want to `/api/v1/auth/logout` 호출해서 로그아웃할 수 있어, so that Refresh Token이 즉시 무효화되고 Access Token이 블랙리스트 등록된다
18. As a 로그아웃한 사용자, I want to 블랙리스트된 Access Token으로 API 호출 시 AUTH_013 에러를 받아, so that 로그아웃이 즉시 효력을 가진다
19. As a 비로그인 사용자, I want to 인증 필요 API 호출 시 AUTH_006 에러를 받아, so that 로그인 화면으로 안내받을 수 있다
20. As a 다른 팀원 (관광지/커뮤니티/결제 도메인 담당), I want to `Account` 엔티티가 존재해 FK 참조할 수 있어, so that 내 도메인 엔티티 구현을 시작할 수 있다
21. As a 운영자, I want to 로그/응답에 비밀번호가 절대 노출되지 않아, so that 보안 사고 위험이 없다
22. As a 클라이언트 개발자, I want to 모든 에러가 `{ code, message }` 통일 포맷으로 내려와, so that 에러 핸들링이 단순해진다

## Implementation Decisions

### 도메인 모델

- **Account 엔티티** (테이블명 `users`): id, email, password (BCrypt 해시), nickname, phoneNumber, profileImageUrl, language, companionScore, companionReviewCount, role (enum), status (enum), suspendedUntil, createdAt, updatedAt, deletedAt
- **Role enum**: `USER`, `MERCHANT`, `ADMIN`. 회원가입 시 항상 `USER`. 상인 승격은 후속 PRD (상인 신청 승인 흐름).
- **Status enum**: `ACTIVE`, `SUSPENDED`, `DELETED`. 정지 처리는 후속 PRD에서 admin 흐름이 변경. 이번 PRD는 enum 정의 + 로그인 시 status 체크만.
- **Soft delete**: `deletedAt` 컬럼 + JPA `@Where` 절. 회원 탈퇴는 후속 PRD.

### 모듈

- **M1 AccountRepository**: Spring Data JPA. `findByEmailAndDeletedAtIsNull`, `existsByEmail`, `existsByNickname`
- **M2 PasswordHasher** (deep): `hash(raw): String`, `matches(raw, hashed): boolean`. BCrypt 구현 캡슐화
- **M3 TokenIssuer** (deep): `issueAccess(userId, role): String`, `issueRefresh(userId): TokenWithId`, `verifyAccess(token): Claims`, `parseRefresh(token): Claims`. JWT 라이브러리, 서명키, 만료 시간(30분/7일), claim 구조 캡슐화
- **M4 RefreshTokenStore** (deep): Redis 기반. `save(userId, tokenId, ttl)`, `exists(userId, tokenId): boolean`, `rotate(userId, oldId, newId)`, `delete(userId)`. 키 형식 `auth:refresh:{userId}` 캡슐화
- **M5 AccessTokenBlacklist** (deep): Redis 기반. `add(tokenId, remainingTtl)`, `contains(tokenId): boolean`. 키 형식 `auth:blacklist:{tokenId}` 캡슐화
- **M6 SignupService**: M1+M2 조합. 이메일/닉네임 중복 체크 → 비밀번호 해시 → Account 저장 → 회원가입 이벤트 발행 (Wallet 도메인 후속 처리용 stub)
- **M7 LoginService**: M1+M2+M3+M4 조합. 이메일 조회 → status 체크 → 비밀번호 비교 → 요청 페이지의 요구 role 체크 → 토큰 쌍 발급 → Refresh 저장
- **M8 ReissueService**: M3+M4 조합. Refresh Token 검증 → Redis 존재 확인 → 새 토큰 쌍 발급 → Rotation
- **M9 LogoutService**: M4+M5 조합. Refresh 삭제 + Access 블랙리스트 등록
- **M10 JwtAuthenticationFilter** + **SecurityConfig**: Authorization Bearer 헤더 추출 → M3 검증 → M5 블랙리스트 체크 → SecurityContext 설정. URL별 권한 매핑: `/users/**` = USER, `/merchants/**` = MERCHANT, `/admin/**` = ADMIN
- **M11 Auth Controllers** (3개): UserAuthController, MerchantAuthController, AdminAuthController + 공통 AuthTokenController (reissue/logout)
- **M12 공통 에러 인프라**: `ErrorCode` enum (AUTH_001 ~ AUTH_013만), `ApiResponse<T>`, `BusinessException`, `GlobalExceptionHandler`. 다른 도메인 코드는 후속 PRD에서 추가

### API 계약

| Method | Path | 요청 | 응답 |
|--------|------|------|------|
| POST | `/api/v1/users/auth/signup` | email, password, nickname, phoneNumber | userId, email, nickname, role, status |
| POST | `/api/v1/users/auth/login` | loginId, password | accessToken (body), refreshToken (HttpOnly Cookie), role |
| POST | `/api/v1/merchants/auth/login` | loginId, password | accessToken, refreshToken, role |
| POST | `/api/v1/admin/auth/login` | loginId, password | accessToken, refreshToken, role |
| POST | `/api/v1/auth/reissue` | (Cookie의 refreshToken) | accessToken |
| POST | `/api/v1/auth/logout` | (Authorization Bearer) | 204 |

### 보안 정책

- Access Token: JWT, 30분, claim = `userId`, `role`, `email`. 클라이언트는 메모리 보관, 매 요청 `Authorization: Bearer` 헤더 전송
- Refresh Token: JWT, 7일, claim = `userId`, `tokenId`. HttpOnly Cookie 전달, Redis 저장 (`auth:refresh:{userId}` = tokenId, TTL = 7일)
- Refresh Token Rotation: 재발급 시 매번 새 tokenId 생성, 이전 tokenId Redis 갱신
- Access Token Blacklist: 로그아웃 시 Redis (`auth:blacklist:{tokenId}`, TTL = 남은 만료 시간)
- 비밀번호: BCrypt 단방향, 최소 8자 + 영문 + 숫자 + 특수문자, 응답 JSON 및 로그 절대 미노출
- CORS: 로컬 `http://localhost:3000`, `http://localhost:5173` 허용. `allowCredentials=true`. Dev/Prod Origin은 application 프로파일별 분리

### 에러 코드 (AUTH_*)

이번 PRD에서 정의 및 사용:
- `AUTH_001` 401 로그인 실패
- `AUTH_002` 401 Access Token 만료
- `AUTH_003` 401 Access Token 변조/서명 오류
- `AUTH_004` 401 Refresh Token 만료
- `AUTH_005` 401 Refresh Token 변조
- `AUTH_006` 401 인증 토큰 없음
- `AUTH_007` 403 권한 부족
- `AUTH_008` 409 이메일 중복
- `AUTH_009` 409 닉네임 중복
- `AUTH_010` 400 비밀번호 형식
- `AUTH_011` 400 이메일 형식
- `AUTH_012` 403 정지된 계정
- `AUTH_013` 401 블랙리스트 토큰

### 의존성 추가 (build.gradle)

- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT)
- `spring-security-crypto` (BCrypt — Security 스타터에 포함)
- Querydsl 초기 세팅은 이번 PRD에 포함하지 않음 (회원가입/로그인 동적 쿼리 불필요). 정민교 ADR-007 책임이지만 별도 PRD/이슈로 분리

### 이벤트

- `UserRegisteredEvent` (Spring ApplicationEvent) — 회원가입 성공 시 발행. 이번 PRD는 발행만, 핸들러는 없음. Wallet 도메인 (신현민 담당) 후속 PRD에서 이벤트 리스너로 Wallet 생성

## Testing Decisions

### 좋은 테스트 기준

- **외부 동작만 테스트**, 구현 디테일 X. Repository 호출 횟수 검증 같은 것 금지. "주어진 입력 → 관찰 가능한 출력 / 상태 변화"만 검증
- **deep module 단위 테스트 우선** — JWT 라이브러리 바뀌어도 테스트 유지되는 수준의 추상화
- **Slice 테스트 활용** — `@DataJpaTest`, `@WebMvcTest`로 의존 최소화
- **통합 테스트 1~2개로 핵심 흐름 커버** — 회원가입 → 로그인 → 인증 API 호출 → 재발급 → 로그아웃 end-to-end

### 모듈별 테스트 계획

| 모듈 | 테스트 유형 | 핵심 케이스 |
|------|-----------|------------|
| **M2 PasswordHasher** | 단위 | hash 결과가 matches로 검증 성공, 다른 비밀번호는 실패, 같은 입력의 hash가 매번 다른 결과 (salt) |
| **M3 TokenIssuer** | 단위 | issue → verify 성공, 만료된 토큰 verify 실패, 변조된 토큰 verify 실패, claim 추출 정확성 |
| **M4 RefreshTokenStore** | 단위 (Testcontainers Redis 또는 임베디드) | save → exists true, delete 후 exists false, rotate 후 old false / new true, TTL 만료 후 false |
| **M5 AccessTokenBlacklist** | 단위 (Redis) | add → contains true, TTL 만료 후 contains false |
| **M6 SignupService** | 단위 (Repository fake) | 정상 가입 성공, 이메일 중복 → AUTH_008, 닉네임 중복 → AUTH_009, 비밀번호 형식 → AUTH_010, 이벤트 발행 확인 |
| **M7 LoginService** | 단위 (Repository / TokenIssuer fake) | 정상 로그인 → 토큰 쌍 반환, 비번 실패 → AUTH_001, 정지 → AUTH_012, role 미스매치 → AUTH_007 |
| **M8 ReissueService** | 단위 | 정상 재발급 → 새 토큰 쌍, Refresh 만료 → AUTH_004, Redis 미존재 → AUTH_005, Rotation 후 이전 토큰 무효 |
| **M10 JwtAuthenticationFilter** | `@WebMvcTest` | 유효 토큰 → SecurityContext 설정, 만료 → AUTH_002, 변조 → AUTH_003, 토큰 없음 + 보호 URL → AUTH_006, 블랙리스트 → AUTH_013, role 미스매치 → AUTH_007 |
| **End-to-End** | `@SpringBootTest` + Testcontainers (MySQL + Redis) | signup → login → 인증 API 호출 → reissue → logout → 호출 시 AUTH_013 |

### Prior Art

현재 코드베이스에 prior art 없음 (스켈레톤 상태). 이번 PRD의 테스트가 향후 모든 도메인의 테스트 컨벤션 기준이 됨. 따라서 패턴을 의식적으로 잡을 것:
- 테스트 클래스명: `{Module}Test` (단위), `{Module}IntegrationTest` (Spring 컨텍스트)
- AssertJ + JUnit 5
- Fake / Stub은 manual 작성 우선, Mockito는 외부 의존성 (이벤트 발행 등)만

## Out of Scope

- F-USER-001 마이페이지 (`GET /users/me`, `PATCH /users/me`, `GET /users/me/home`) — 별도 PRD
- F-ADMIN-* 관리자 전체 — 별도 PRD (정지 처리 흐름, 대시보드, FAQ, 배너, 신고, 콘텐츠, 광고, 인증, 환불)
- 상인 신청 / 승인 / 거절 (merchant_applications 테이블) — 별도 PRD
- 정지 이력 (suspensions 테이블) — 관리자 PRD
- Wallet 자동 생성 — 신현민 담당, UserRegisteredEvent 핸들러로 별도 구현
- 비밀번호 재설정 / 비밀번호 변경 — 별도 PRD
- 회원 탈퇴 (soft delete 실행) — 별도 PRD
- 소셜 로그인 (OAuth2) — ADR에서 배제됨
- Querydsl 초기 세팅 — 정민교 ADR-007 책임이지만 사용처 (관리자 검색) 등장 시 별도 PRD
- 배포 인프라 (GitHub Actions, Docker, EC2) — 정민교 담당이나 코드 누적 후 별도 PRD
- Rate limiting (로그인 5회/분, 회원가입 3회/10분) — 인프라 PRD에서 (운영 보안 정책 11번 문서)
- AI FAQ 관리, 고객센터 관리, 환불 관리 등 관리자 하위 도메인 — 각각 별도 PRD

## Further Notes

- `docs/admin-domain-plan.md`와 `docs/account-admin-api-convention.md`가 공존. 후자가 최신 통합본. 이번 PRD는 **convention 문서 기준**. 그러나 admin login endpoint는 양 문서 동일.
- 로그인 endpoint를 3개로 분리하는 정책 = ADR / convention 문서의 핵심 결정. role 검증은 controller 단이 아니라 LoginService 인자 (`requiredRole`)로 받아 처리 → endpoint별 controller에서 명시적으로 다른 role 전달
- Refresh Token을 HttpOnly Cookie + Redis 둘 다 사용하는 이유: Cookie = XSS 방어, Redis = 서버 측 즉시 무효화 (로그아웃 / 강제 탈퇴)
- 후속 PRD `/to-issues` 분할 시 후보 슬라이스: (1) Account 엔티티 + 공통 에러 인프라 + build.gradle 의존성, (2) PasswordHasher + SignupService + signup endpoint, (3) TokenIssuer + RefreshTokenStore + LoginService + 3개 login endpoint, (4) ReissueService + AccessTokenBlacklist + reissue/logout endpoint, (5) JwtAuthenticationFilter + SecurityConfig + URL 권한 매핑, (6) End-to-end 통합 테스트
- 이번 PRD 완료 후 다음 PRD 우선순위 후보: F-USER-001 마이페이지 → 관리자 대시보드 → 유저 관리 (정지/해제) → 상인 신청 처리

Generated with Claude Code
