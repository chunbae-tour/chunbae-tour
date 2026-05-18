## Parent

KAN-23 (Epic: Auth foundation — 회원가입 + 로그인 + JWT 인증)

## What to build

상인 페이지와 관리자 페이지 전용 로그인 endpoint를 추가하고, Spring Security URL 권한 매핑을 확장한다. 사용자/상인/관리자 페이지를 분리 운영하는 정책에 따라 각 endpoint는 해당 role을 요구하고, 미스매치 시 AUTH_007을 반환한다.

### 동작 흐름

1. `POST /api/v1/merchants/auth/login`: LoginService에 `requiredRole=MERCHANT` 전달
2. `POST /api/v1/admin/auth/login`: LoginService에 `requiredRole=ADMIN` 전달
3. 계정 role이 요구 role과 다르면 AUTH_007 (예: USER가 admin login 시도)
4. role 매치 시 정상 토큰 발급 (S2/S3와 동일 흐름)

### URL 권한 매핑 확장 (SecurityConfig)

- `/api/v1/users/auth/**` = permitAll
- `/api/v1/merchants/auth/**` = permitAll
- `/api/v1/admin/auth/**` = permitAll
- `/api/v1/auth/**` = permitAll (reissue, logout — logout은 인증 필요지만 필터가 처리)
- `/api/v1/users/**` = hasRole("USER")
- `/api/v1/merchants/**` = hasRole("MERCHANT")
- `/api/v1/admin/**` = hasRole("ADMIN")
- 그 외 = authenticated

### 포함 범위

- **MerchantAuthController.login**: `POST /api/v1/merchants/auth/login` 매핑
- **AdminAuthController.login**: `POST /api/v1/admin/auth/login` 매핑
- **SecurityConfig URL 매핑 확장**: 위 정책 반영
- **LoginService**: 이미 S2에서 `requiredRole` 인자 구조 잡혀있음 → 추가 변경 없음 (S2 설계 검증)
- **임시 보호 endpoint**: 상인/관리자 페이지 본격 endpoint는 별도 PRD. 이번 슬라이스는 통합 테스트용 임시 ping 둘 (`/api/v1/merchants/me/ping`, `/api/v1/admin/me/ping`) 추가 후 PRD 완료 시 제거 검토

### 시드 데이터 (테스트용)

이번 슬라이스 테스트는 MERCHANT, ADMIN role 계정이 필요하다. 회원가입 흐름은 USER만 만들므로 통합 테스트에서는 SQL/Repository로 직접 시드한다 (운영 흐름 아님).

## Acceptance criteria

- [ ] `MerchantAuthController.login` endpoint (`POST /api/v1/merchants/auth/login`)
- [ ] `AdminAuthController.login` endpoint (`POST /api/v1/admin/auth/login`)
- [ ] `SecurityConfig` URL 권한 매핑 확장 (위 정책 반영)
- [ ] 임시 `GET /api/v1/merchants/me/ping`, `GET /api/v1/admin/me/ping` endpoint
- [ ] LoginService 통합 테스트: USER 계정이 `/merchants/auth/login` 호출 시 AUTH_007, MERCHANT 계정이 정상 시 토큰 발급
- [ ] LoginService 통합 테스트: USER 계정이 `/admin/auth/login` 호출 시 AUTH_007, ADMIN 계정이 정상 시 토큰 발급
- [ ] 통합 테스트: USER 토큰으로 `/api/v1/merchants/me/ping` 호출 시 AUTH_007 (URL 권한 매핑 검증)
- [ ] 통합 테스트: USER 토큰으로 `/api/v1/admin/me/ping` 호출 시 AUTH_007
- [ ] 통합 테스트: MERCHANT 토큰으로 `/api/v1/merchants/me/ping` 정상 200
- [ ] 통합 테스트: ADMIN 토큰으로 `/api/v1/admin/me/ping` 정상 200
- [ ] 통합 테스트: 모든 페이지의 reissue/logout은 공통 `/api/v1/auth/**` 사용 (분리 안 함) 검증

## Blocked by

S2 (사용자 로그인 + JWT 인증 필터)

S5는 S3/S4와 병렬 진행 가능 (의존 없음). 권장: S2 머지 후 S3/S4/S5 동시 작업
