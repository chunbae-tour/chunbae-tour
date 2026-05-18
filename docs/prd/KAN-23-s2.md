## Parent

KAN-23 (Epic: Auth foundation — 회원가입 + 로그인 + JWT 인증)

## What to build

일반 사용자 로그인 endpoint와 JWT 인증 필터를 end-to-end로 구현한다. 로그인 성공 시 Access Token + Refresh Token 쌍을 발급하고, 인증 필터가 이후 보호된 endpoint 요청에서 토큰을 검증해 SecurityContext에 사용자 정보를 설정한다.

이 슬라이스는 Refresh Token Rotation, Redis 저장, HttpOnly Cookie 전달은 포함하지 않는다 (S3에서). 이번 슬라이스의 Refresh Token은 Response Body로 임시 전달하고 stateless 검증만 한다.

### 동작 흐름

1. `POST /api/v1/users/auth/login` 에 이메일/비밀번호 전달
2. AccountRepository로 이메일 조회 (없으면 AUTH_001)
3. Status 체크 (SUSPENDED → AUTH_012)
4. 비밀번호 BCrypt 비교 (불일치 → AUTH_001)
5. 요청 페이지의 요구 role 체크 (이번 슬라이스는 USER 고정, 미스매치 → AUTH_007)
6. TokenIssuer로 Access (30분) + Refresh (7일) 발급
7. 응답: `{ accessToken, refreshToken, role }` (S3에서 refreshToken을 Cookie로 전환)

### 인증 필터 흐름

1. Authorization 헤더에서 Bearer 토큰 추출 (없으면 다음 필터로 통과, 보호 URL이면 EntryPoint가 AUTH_006 응답)
2. TokenIssuer로 verify (만료 → AUTH_002, 서명 오류/변조 → AUTH_003)
3. Claim에서 userId/role 추출 → `Authentication` 객체 생성 → SecurityContext 저장

### 포함 범위

- **TokenIssuer** (deep module): `issueAccess(userId, role, email): String`, `issueRefresh(userId): TokenWithId(tokenId, token)`, `verifyAccess(token): AccessClaims(userId, role, email, tokenId)`, `verifyRefresh(token): RefreshClaims(userId, tokenId)`. JJWT 라이브러리, HMAC SHA-256 서명키, 만료 시간 (application.yml `jwt.access-token-ttl`, `jwt.refresh-token-ttl`), claim 구조 캡슐화. **이번 슬라이스에서 RefreshTokenStore 없이 stateless 검증만 한다.**
- **LoginService**: `login(loginId, password, requiredRole): TokenPair`. requiredRole 인자로 endpoint별 권한 차이 처리 (S5 대비)
- **UserAuthController.login**: `POST /api/v1/users/auth/login` 매핑, `requiredRole=USER` 고정 전달
- **JwtAuthenticationFilter**: `OncePerRequestFilter` 상속. SecurityContext에 `UsernamePasswordAuthenticationToken` 저장 (principal=userId, authorities=`ROLE_{role}`)
- **SecurityConfig**: 필터 체인 구성. URL 매핑 `/api/v1/users/auth/**` = permitAll, `/api/v1/users/**` = hasRole("USER"), 그 외 인증 필요. CSRF disable (API), session stateless, `AuthenticationEntryPoint` (AUTH_006), `AccessDeniedHandler` (AUTH_007)
- **임시 보호 endpoint**: `GET /api/v1/users/me/ping` → 200 + `{ userId }`. 마이페이지는 별도 PRD, 인증 흐름 검증 용도로만. PRD 완료 시 제거
- **에러 코드 추가**: AUTH_001 (로그인 실패), AUTH_002 (Access 만료), AUTH_003 (Access 변조), AUTH_006 (인증 필요), AUTH_007 (권한 부족), AUTH_012 (정지 계정)

## Acceptance criteria

- [ ] `TokenIssuer` 클래스 (JJWT 캡슐화, HMAC SHA-256, application.yml 설정값 주입)
- [ ] `LoginService` 구현 (requiredRole 인자 지원)
- [ ] `UserAuthController.login` endpoint (`POST /api/v1/users/auth/login`)
- [ ] `JwtAuthenticationFilter` (OncePerRequestFilter)
- [ ] `SecurityConfig` 필터 체인 구성 + URL 권한 매핑 (USER만 우선)
- [ ] `AuthenticationEntryPoint` (AUTH_006), `AccessDeniedHandler` (AUTH_007) 적용 — `ApiResponse` 포맷으로 응답
- [ ] 임시 `GET /api/v1/users/me/ping` endpoint
- [ ] AUTH_001/002/003/006/007/012 에러 코드 ErrorCode enum에 추가
- [ ] TokenIssuer 단위 테스트 (issue → verify 성공, 만료된 토큰 verify 실패, 변조된 토큰 verify 실패, claim 정확성, 다른 키로 서명한 토큰 verify 실패)
- [ ] LoginService 단위 테스트 (정상 로그인 → 토큰 쌍, 비번 실패 → AUTH_001, 정지 → AUTH_012, role 미스매치 → AUTH_007, 존재하지 않는 이메일 → AUTH_001)
- [ ] JwtAuthenticationFilter 단위 또는 `@WebMvcTest`: 유효 토큰 → SecurityContext 설정, 만료 → AUTH_002, 변조 → AUTH_003, 토큰 없이 보호 URL → AUTH_006
- [ ] 통합 테스트 (`@SpringBootTest` + Testcontainers MySQL): signup → login → ping (200), 잘못된 비번 → AUTH_001, 토큰 없이 ping → AUTH_006
- [ ] application.yml에 `jwt.secret`, `jwt.access-token-ttl`, `jwt.refresh-token-ttl` 추가 (local/prod 분리, secret은 환경변수)

## Blocked by

S1 (사용자 회원가입 endpoint)
