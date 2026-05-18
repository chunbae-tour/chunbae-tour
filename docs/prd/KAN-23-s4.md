## Parent

KAN-23 (Epic: Auth foundation — 회원가입 + 로그인 + JWT 인증)

## What to build

로그아웃 endpoint와 Access Token 블랙리스트를 구현해 서버 측에서 토큰을 즉시 무효화한다. 로그아웃 시 Refresh Token은 Redis에서 삭제하고, Access Token은 남은 만료 시간 동안 블랙리스트에 등록한다. JWT 인증 필터는 이후 요청마다 블랙리스트를 체크한다.

### 동작 흐름

1. 클라이언트가 `POST /api/v1/auth/logout` 호출 (Authorization Bearer + HttpOnly Cookie 자동 전송)
2. 인증 필터가 Access Token 검증 후 SecurityContext에 사용자 정보 설정 (정상 통과)
3. LogoutService 호출:
   - Access Token claim에서 tokenId(jti) + 남은 만료 시간 추출
   - AccessTokenBlacklist.add(tokenId, remainingTtl)
   - RefreshTokenStore.delete(userId)
4. 응답: 204 No Content + Refresh Cookie 삭제 (Max-Age=0)

### 인증 필터 변경 (S2 → S4 추가)

- Access Token verify 성공 후 AccessTokenBlacklist.contains(tokenId) 체크
- 블랙리스트에 있으면 AUTH_013 응답
- 정상 토큰만 SecurityContext 설정

### 포함 범위

- **AccessTokenBlacklist** (deep module): Redis 기반. `add(tokenId, remainingTtl)`, `contains(tokenId): boolean`. 키 형식 `auth:blacklist:{tokenId}` 캡슐화
- **LogoutService**: AccessTokenBlacklist + RefreshTokenStore 조합
- **AuthTokenController.logout**: `POST /api/v1/auth/logout` 매핑
- **JwtAuthenticationFilter 변경**: verify 후 블랙리스트 체크 추가
- **TokenIssuer 변경**: Access Token claim에 `jti` (tokenId, UUID) 추가 (이전에는 Refresh에만 tokenId). verifyAccess가 jti를 반환하도록 확장
- **Cookie 삭제 응답**: 로그아웃 시 Refresh Cookie를 Max-Age=0 으로 set
- **에러 코드 추가**: AUTH_013 (블랙리스트 토큰)

## Acceptance criteria

- [ ] `AccessTokenBlacklist` 클래스 (Redis StringRedisTemplate, 키 `auth:blacklist:{tokenId}`)
- [ ] `LogoutService` 구현
- [ ] `AuthTokenController.logout` endpoint (`POST /api/v1/auth/logout`)
- [ ] `JwtAuthenticationFilter` 에 블랙리스트 체크 로직 추가
- [ ] `TokenIssuer` 의 Access Token claim에 `jti` 추가, verifyAccess가 tokenId 포함하여 반환
- [ ] 로그아웃 응답 시 Refresh Cookie 삭제 (Max-Age=0, 같은 Path)
- [ ] AUTH_013 에러 코드 추가
- [ ] AccessTokenBlacklist 단위 테스트 (Testcontainers Redis): add → contains true, TTL 만료 후 contains false, add 시 음수 TTL 들어오면 즉시 만료
- [ ] LogoutService 단위 테스트: 정상 로그아웃 시 블랙리스트 등록 + Refresh 삭제 호출 검증 (외부 동작만)
- [ ] JwtAuthenticationFilter 단위/`@WebMvcTest`: 블랙리스트 등록된 토큰 → AUTH_013, 정상 토큰 → 통과
- [ ] 통합 테스트: signup → login → ping(200) → logout → 같은 Access Token으로 ping → AUTH_013, 같은 Refresh Cookie로 reissue → AUTH_005

## Blocked by

S2 (사용자 로그인 + JWT 인증 필터)

권장 순서: S2 → S3 → S4. S3가 RefreshTokenStore를 도입하므로 S4의 RefreshTokenStore.delete 호출이 자연스러움. S3 없이 S4만 진행 시 RefreshTokenStore를 이 슬라이스에서 도입해야 함.
