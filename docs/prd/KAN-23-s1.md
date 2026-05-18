## Parent

KAN-23 (Epic: Auth foundation — 회원가입 + 로그인 + JWT 인증)

## What to build

일반 사용자 회원가입 endpoint를 end-to-end로 구현한다. 이 슬라이스는 첫 tracer bullet이므로 Auth 도메인 전체에서 사용할 공통 인프라 (의존성, 패키지, 에러 처리, 응답 포맷)도 함께 셋업한다.

### 동작 흐름

`POST /api/v1/users/auth/signup` 호출 시 이메일/비밀번호/닉네임을 받아 검증, 중복 체크, BCrypt 해시 후 `users` 테이블에 USER 권한 ACTIVE 상태 계정을 생성하고 201 응답을 반환한다. 회원가입 성공 시 `UserRegisteredEvent`를 발행한다 (핸들러는 후속 PRD).

### 포함 범위

- **의존성**: `spring-boot-starter-security`, `jjwt-api/impl/jackson` (S2~S5에서 활용 예정이지만 이번 슬라이스에서 한 번에 셋업)
- **패키지 구조**: `com.chunbaetour.domain.auth` (Auth 도메인), `com.chunbaetour.domain.common` (공통 인프라)
- **Account 엔티티** (테이블 `users`): id, email, password, nickname, profileImageUrl(null), language(기본값 ko), companionScore(0), companionReviewCount(0), role(USER 고정), status(ACTIVE 고정), suspendedUntil(null), createdAt, updatedAt, deletedAt(null). Soft delete `@Where(deletedAt IS NULL)`
- **Role enum**: USER, MERCHANT, ADMIN. Status enum: ACTIVE, SUSPENDED, DELETED
- **AccountRepository** (Spring Data JPA): `existsByEmail`, `existsByNickname`, `findByEmailAndDeletedAtIsNull` (S2에서 사용, 이번 슬라이스는 정의만)
- **PasswordHasher** (deep module): BCrypt 캡슐화. `hash(raw)`, `matches(raw, hashed)`
- **SignupService**: 입력 검증 → 중복 체크 → 해시 → 저장 → 이벤트 발행
- **UserAuthController**: `POST /api/v1/users/auth/signup` 매핑
- **공통 에러 인프라**: `ErrorCode` enum (이번 슬라이스에서 AUTH_008/009/010/011만 정의, 후속 슬라이스에서 추가), `BusinessException`, `GlobalExceptionHandler`, `ApiResponse<T>` (성공/에러 통일 포맷 `{ code, message, data }`)
- **UserRegisteredEvent**: Spring `ApplicationEvent`. userId, email, nickname 포함. 발행만, 핸들러 없음
- **Bean Validation**: 요청 DTO에 `@Email`, `@Pattern` (비밀번호 정책 정규식, 닉네임 형식 정규식), `@Size` 적용 → 위반 시 `MethodArgumentNotValidException` → `GlobalExceptionHandler`가 AUTH_010/011로 변환
- **닉네임 형식 정책**: 2~20자, 한글/영문/숫자/`_`/`-`만 허용 (`^[\p{L}\p{N}_-]{2,20}$`). 공백/이모지/특수문자 차단

### 비밀번호 정책 (sa-docs/11)

- 최소 8자 이상
- 영문 + 숫자 + 특수문자 포함
- BCrypt 단방향 해시
- 응답/로그 절대 미노출 (DTO에 비밀번호 필드 없음)

## Acceptance criteria

- [ ] `build.gradle`에 `spring-boot-starter-security`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson` 의존성 추가
- [ ] `Account` 엔티티 (테이블 `users`) JPA 매핑 완료, `@Where` 절로 soft delete 적용
- [ ] `Role`, `Status` enum 정의
- [ ] `AccountRepository` Spring Data JPA 인터페이스 정의 (`existsByEmail`, `existsByNickname`, `findByEmailAndDeletedAtIsNull`)
- [ ] `PasswordHasher` 클래스 (BCrypt 캡슐화)
- [ ] `SignupService` 구현
- [ ] `UserAuthController` 의 `POST /api/v1/users/auth/signup` endpoint
- [ ] 공통 `ErrorCode` enum (AUTH_008/009/010/011), `BusinessException`, `GlobalExceptionHandler`, `ApiResponse<T>` 정의
- [ ] `UserRegisteredEvent` Spring ApplicationEvent 발행 코드
- [ ] PasswordHasher 단위 테스트 (hash → matches 성공, 다른 비밀번호 실패, 같은 입력의 해시가 매번 다름)
- [ ] SignupService 단위 테스트 (정상 가입, AUTH_008 이메일 중복, AUTH_009 닉네임 중복, AUTH_010 비밀번호 형식, 이벤트 발행 확인)
- [ ] 통합 테스트 (`@SpringBootTest` + Testcontainers MySQL): signup 성공 201 응답, 중복 이메일 → 409 + AUTH_008, 잘못된 비밀번호 형식 → 400 + AUTH_010
- [ ] 응답에 비밀번호 필드 미포함 검증 (테스트 또는 코드 리뷰)
- [ ] Spring Security 기본 설정으로 `/api/v1/users/auth/signup` 은 익명 접근 허용 (필터 체인은 S2에서 본격 구성, 이번 슬라이스는 최소 `permitAll`)

## Blocked by

None - can start immediately
