## Parent

KAN-23 (Epic: Auth foundation — 회원가입 + 로그인 + JWT 인증)

## What to build

Refresh Token Rotation을 적용한 Access Token 재발급 endpoint를 구현한다. Refresh Token은 Redis에 저장되어 서버 측에서 무효화 가능하고, HttpOnly Cookie로 클라이언트에 전달되어 XSS 공격을 방어한다.

S2에서 Body로 임시 전달하던 Refresh Token을 이 슬라이스에서 HttpOnly Cookie로 전환한다.

### 동작 흐름 (재발급)

1. 클라이언트가 `POST /api/v1/auth/reissue` 호출 (HttpOnly Cookie의 refreshToken 자동 전송)
2. Cookie에서 Refresh Token 추출 (없음 → AUTH_005)
3. TokenIssuer로 verify (만료 → AUTH_004, 변조 → AUTH_005)
4. Claim에서 userId, tokenId 추출
5. RefreshTokenStore.exists(userId, tokenId) 확인 (불일치 또는 미존재 → AUTH_005)
6. 새 Access Token + 새 Refresh Token (새 tokenId) 발급
7. RefreshTokenStore.rotate(userId, oldTokenId, newTokenId) — 이전 토큰 무효화
8. 응답: `{ accessToken }` Body + 새 refreshToken HttpOnly Cookie

### 로그인 흐름 변경 (S2 → S3 전환)

- 로그인 성공 시 Refresh Token을 RefreshTokenStore.save(userId, tokenId, 7일 TTL) 호출
- 응답 Body의 `refreshToken` 필드 제거
- HttpOnly Cookie로 전달 (Secure, SameSite=Lax, Path=`/api/v1/auth`)

### 포함 범위

- **RefreshTokenStore** (deep module): Redis 기반. `save(userId, tokenId, ttl)`, `exists(userId, tokenId): boolean`, `rotate(userId, oldId, newId)`, `delete(userId)`. 키 형식 `auth:refresh:{userId}` 캡슐화. Lua 스크립트 또는 Redis 트랜잭션으로 rotate 원자성 보장
- **ReissueService**: TokenIssuer + RefreshTokenStore 조합. Rotation 시나리오 처리
- **AuthTokenController.reissue**: `POST /api/v1/auth/reissue` 매핑
- **Cookie 유틸**: `ResponseCookie` 빌더로 HttpOnly + Secure (prod) + SameSite + Path + Max-Age 설정
- **LoginService 수정**: Refresh를 Cookie + Redis 저장 흐름으로 전환
- **에러 코드 추가**: AUTH_004 (Refresh 만료), AUTH_005 (Refresh 변조/불일치)

## Acceptance criteria

- [ ] `RefreshTokenStore` 클래스 (Redis StringRedisTemplate 사용, 키 `auth:refresh:{userId}`)
- [ ] `RefreshTokenStore.rotate` 가 원자적으로 동작 (Lua 또는 트랜잭션) — 동시 재발급 시 한쪽만 성공
- [ ] `ReissueService` 구현
- [ ] `AuthTokenController.reissue` endpoint (`POST /api/v1/auth/reissue`)
- [ ] `LoginService` 변경: Refresh 발급 시 Redis 저장, Body 응답에서 refreshToken 제거, HttpOnly Cookie로 전달
- [ ] `SecurityConfig`: `/api/v1/auth/reissue` permitAll, Cookie 추출 로직
- [ ] application.yml prod 프로파일에서 Cookie Secure=true (local은 false)
- [ ] AUTH_004/005 에러 코드 추가
- [ ] RefreshTokenStore 단위 테스트 (Testcontainers Redis): save → exists true, delete 후 false, rotate 후 old false + new true, TTL 만료 후 false, 동시 rotate 시 한쪽만 성공
- [ ] ReissueService 단위 테스트: 정상 재발급, Refresh 만료 → AUTH_004, Redis 미존재 → AUTH_005, Rotation 후 이전 토큰으로 재요청 시 AUTH_005
- [ ] 통합 테스트: signup → login (Cookie 반환 확인) → reissue (새 Access 반환, 새 Refresh Cookie 반환) → 이전 Refresh Cookie로 재요청 시 AUTH_005
- [ ] HttpOnly Cookie 응답 헤더 검증 테스트

## Blocked by

S2 (사용자 로그인 + JWT 인증 필터)
