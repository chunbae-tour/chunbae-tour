# KAN-23 S1 후속 작업 목록

S1 (회원가입) 완료 직후 진행한 코드 리뷰와 PRD/ERD/기능명세서 cross-check에서 도출된, 이번 슬라이스 범위 밖으로 분리된 항목 모음. 다음 슬라이스 진입 전 또는 별도 PRD/이슈로 처리 대상.

작성 기준: 2026-05-19 (PR #22 push 시점). 기준 커밋: `a91871c`.

---

## 1. 도메인 경계 충돌

### 1.1 `users.companion_score` 타입

- **현재 상태**: ERD `FLOAT DEFAULT 0`, 코드 `float`, API 명세서 응답 예시 `4.3` / `4.8` (소수 1자리)
- **리뷰 우려**: 동행 평점 평균 누적 계산 시 float 정밀도 오차
- **도메인 책임**:
  - `users` 테이블 스키마 = **정민교** (sa-docs/05 회원/인증)
  - `companion_score` 값 갱신 로직 = **임하은** (sa-docs/04 F-CHAT-006 동행 리뷰, sa-docs/01 채팅/매칭)
- **결정 보류**: float 유지. 임하은이 F-CHAT-006 구현 시점에 `BigDecimal` 전환이 필요하면 ADR로 결정
- **트리거**: F-CHAT-006 동행 리뷰 PRD 작성 또는 평균 점수 계산 로직 구현 시
- **변경 시 영향**: ERD `FLOAT → DECIMAL(3,1)` 또는 `DECIMAL(3,2)`, Entity `BigDecimal`, 기존 데이터 마이그레이션(현재 데이터 0건이라 부담 낮음)

### 1.2 `nickname` VARCHAR 길이 정합

- **현재 상태**: ERD `VARCHAR(20)`로 정정 (이전 50). 코드 `@Size(max=20)` + `@Column(length=20)`
- **상태**: 정합 완료 (`4855f0e` 커밋)
- **참고**: sa-docs/05 ERD는 `.git/info/exclude`로 로컬 전용. 팀 공유 시점에 ERD 원본(Notion) 동기화 필요

---

## 2. 하드코딩 / 미구현 필드

### 2.1 `Account.language = "ko"` 하드코딩

- **위치**: `src/main/java/com/chunbaetour/domain/auth/Account.java` Builder 초기화
- **API 명세서 (sa-docs/06)**: `POST /auth/signup` 요청 body에 `language` 포함 정의
- **PRD KAN-23-s1**: language 요청 필드 정의 없음 (기본값 ko로만)
- **결정 보류**: 다국어 사용자 기획 확정 전까지 `ko` 하드코딩 유지
- **트리거**: i18n 도메인 PRD 진입, 또는 외국인 사용자 가입 시나리오 활성화 (sa-docs/01 P4 페르소나 "외국인 관광객")
- **변경 시 작업**: `SignupRequest`에 `language` 필드 추가 + ENUM(`ko`, `en`, `ja`, `zh`) 검증 + Account.registerUser 인자 확장

### 2.2 비밀번호 추가 정책 미구현 (sa-docs/11)

- 현재 정규식: `^(?=.*[A-Za-z])(?=.*\d)(?=.*[\W_]).{8,}$` + `@Size(max=72)`
- **미구현 정책**:
  - 이메일/닉네임과 동일한 비밀번호 금지
  - 연속된 숫자 사용 금지 (예: `12345678`)
- **결정 보류**: S1 범위 외. 별도 PRD 또는 sa-docs/11 정책 강제 시점에 진입
- **트리거**: 보안 정책 강화 요구, 또는 비밀번호 변경 endpoint 구현 시

---

## 3. 문서 SOT 정리 결과

세 문서에서 회원가입 필드 정의가 달라 한 차례 정리함:

| 문서 | 회원가입 요청 필드 (정리 전) | 결정 |
|---|---|---|
| sa-docs/04 F-AUTH-001 | email, password, nickname | **SOT 채택** |
| sa-docs/05 ERD users | (테이블 컬럼만 정의) | nickname VARCHAR(20)으로 정렬 |
| sa-docs/06 API 명세서 | email, password, nickname, **language** | language는 후속 (위 2.1) |
| docs/prd/KAN-23-s1.md | email, password, nickname, **phoneNumber** | phoneNumber 제거 |
| docs/prd/KAN-23-auth-foundation.md | email, password, nickname, **phoneNumber** | phoneNumber 제거 |

- **SOT 정책 합의**: sa-docs/04 기능명세서 = 회원가입 필드 정의 single source of truth
- **현재 코드**: SOT 정렬 완료

---

## 4. 코드 리뷰 후속 (S1 범위 외 deferred)

PR #22 cavecrew-reviewer 리뷰에서 보고됐으나 S1에서 처리하지 않은 항목.

### 4.1 `MethodArgumentNotValidException` 다중 필드 처리

- **위치**: `GlobalExceptionHandler.resolveFieldErrorCode`
- **문제**: 첫 필드 에러만 매핑. email + password 동시 위반 시 client는 한 번에 하나만 확인 가능
- **개선안 옵션**:
  - A. `data` 필드에 위반 필드 리스트 포함
  - B. 우선순위(email → password → nickname) 문서화
- **트리거**: 클라이언트 통합 테스트에서 UX 이슈 발견 시

### 4.2 `ApiResponse<T>` `data` null 직렬화

- **위치**: `src/main/java/com/chunbaetour/domain/common/response/ApiResponse.java`
- **문제**: `@JsonInclude(NON_NULL)` 로 `data: null` 응답에서 키 자체가 사라짐. 클라이언트가 `data` 키 always-present 가정 시 깨짐
- **결정 보류**: 클라이언트 계약 확정 시 정함

### 4.3 `UserRegisteredEvent` `occurredAt` 누락

- **위치**: `src/main/java/com/chunbaetour/domain/auth/event/UserRegisteredEvent.java`
- **문제**: 발급 시각 없음. 다운스트림 리스너 (Wallet 자동 생성, 통계 등)에서 timestamp 필요할 수 있음
- **개선안**: `Instant occurredAt` 추가
- **트리거**: Wallet 자동 생성 핸들러 구현 시 (신현민 후속 PRD)

### 4.4 `BCryptPasswordEncoder` 강도

- **위치**: `src/main/java/com/chunbaetour/domain/auth/PasswordHasher.java`
- **현재**: 기본 strength=10
- **결정 보류**: 보안 정책 baseline 확정 시 명시. sa-docs/11에 BCrypt 강도 정의 없음
- **트리거**: 보안 정책 PRD 또는 운영 보안 감사

### 4.5 통합 테스트 데이터 cleanup 전략

- **위치**: `src/test/java/com/chunbaetour/domain/auth/SignupIntegrationTest.java`
- **현재**: `@AfterEach accountRepository.deleteAll()`. soft-delete 전환 시(`@SQLDelete`) 데이터 누수
- **결정 보류**: 회원 탈퇴 PRD에서 soft-delete 본격 사용 시 truncate 전략 채택

### 4.6 동시 가입 TOCTOU 후속

- **현재 (`37e1e3f`)**: `DataIntegrityViolationException` 캐치 → existsBy* 재조회로 판별
- **잠재 race**: 캐치 후 재조회 사이에 또 다른 가입이 들어와 다시 race 가능 (이론상 매우 낮음)
- **결정 보류**: rate limit (운영 보안 11번 문서 회원가입 3회/10분)로 자연 차단되리라 예상. rate limit 인프라 PRD에서 함께 다룸

### 4.7 `Account.registerUser` defensive null check

- **위치**: `Account.java:registerUser`
- **현재**: `@NotBlank` DTO validation에 의존
- **개선안**: 엔티티 factory에서 `Assert.hasText` 또는 `Objects.requireNonNull`
- **결정 보류**: 도메인 모델 hardening은 별도 리팩토링 PRD

---

## 5. 운영/인프라 후속 (S1 범위 외)

### 5.1 CORS 설정

- **현재**: `SecurityConfig`에 CORS 설정 없음
- **PRD 정의 (KAN-23-auth-foundation 라인 87)**: 로컬 `localhost:3000`, `localhost:5173` 허용, `allowCredentials=true`, profile별 origin 분리
- **트리거**: S5 SecurityConfig 본격 구성 슬라이스, 또는 프론트엔드 연동 시점

### 5.2 Rate limiting

- 회원가입 3회/10분, 로그인 5회/분 (sa-docs/11)
- **결정**: 인프라 PRD에서 처리. S1~S5 auth 범위 외

### 5.3 `language` 필드 후속

- 위 2.1 참조

---

## 6. 작업 진입 가이드

다음 작업자(사용자 또는 Claude)가 이 문서를 보고 결정할 때:

1. **현재 작업이 어떤 도메인인지** → 정민교(회원/인증/마이페이지/관리자/배포) 범위면 1.1 임하은 협의 필요 여부 확인
2. **변경 대상이 sa-docs인지** → sa-docs는 `.git/info/exclude` 제외라 로컬 갱신만 가능. 팀 공유는 Notion 원본 동기화 필요
3. **PRD 수정이 필요한지** → SOT는 sa-docs/04 기능명세서. PRD가 SOT를 위반하면 PRD 수정
4. **commit prefix** → docs/03-GIT-WORKFLOW.md 컨벤션 따름. base 브랜치 `develop`

---

## 7. 참조

- PR: https://github.com/chunbae-tour/chunbae-tour/pull/22
- 기준 커밋: `a91871c docs(prd): KAN-23 phoneNumber 제거 + 닉네임 정책 명시`
- 관련 문서:
  - `docs/prd/KAN-23-auth-foundation.md` (Epic PRD)
  - `docs/prd/KAN-23-s1.md` ~ `KAN-23-s5.md` (슬라이스 PRD)
  - `sa-docs/04_기능_명세서` F-AUTH-001 (회원가입 필드 SOT)
  - `sa-docs/05_ERD` users 테이블 (스키마 SOT)
  - `sa-docs/06_API_명세서` (요청/응답 예시)
  - `sa-docs/11_운영_보안_정책_설계서` (비밀번호/Rate limit 정책)
  - `sa-docs/12_공통_에러코드_설계서` (AUTH_* 코드 정의)
  - `AGENTS.md`, `docs/agent-harness/01-AGENT-HARNESS.md` (작업 원칙)
