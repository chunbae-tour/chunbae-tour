# Refresh Cookie SameSite / 도메인 정책 가이드 (KAN-125, Epic KAN-64 S5)

> Refresh Token Cookie의 SameSite 정책 + 운영 도메인 구성 결정 가이드.
> KAN-23 S3 followup §1 해소 → 본 가이드로 일원화.

## 핵심 결정 트리

```
운영 프론트엔드 도메인 == 운영 백엔드 도메인 (같은 eTLD+1)?
├─ YES (예: 프론트 web.chunbae.tour + API api.chunbae.tour)
│   → SameSite=Lax 유지 (기본값)
│   → CSRF 기본 방어 + UX 깨짐 없음
│
└─ NO (예: 프론트 chunbae-front.app + API chunbae-api.com)
    → SameSite=None + Secure=true 전환
    → 브라우저 요구사항: Secure=true 없으면 cookie 자체 무시 → 로그인 silent 실패
    → CSRF 방어 약화 → CORS allowedOrigins 엄격 관리 + Origin 검증 필수
```

**eTLD+1 정의**: "effective Top-Level Domain + 1". `example.com`은 eTLD+1. `web.example.com`과 `api.example.com`은 같은 eTLD+1 → same-site. `web.com`과 `api.org`는 다른 eTLD+1 → cross-site.

판단 도구: <https://browserleaks.com/etld> 또는 Chrome DevTools Application > Cookies 탭에서 `SameSite` 컬럼 + `same-site` 동작 확인.

## 환경별 조합 표

| 환경 | 프론트 origin | 백엔드 origin | 관계 | `secure` | `same-site` | 비고 |
|---|---|---|---|---|---|---|
| **local** | `http://localhost:3000`, `:5173` | `http://localhost:8080` | same-site (localhost) | `false` | `Lax` | localhost cross-port는 브라우저가 same-site 취급 |
| **prod (Case A — 같은 eTLD+1)** | `https://web.chunbae.tour` | `https://api.chunbae.tour` | same-site | `true` | `Lax` | **권장** — 별도 작업 없이 기본 default 사용 |
| **prod (Case B — 다른 eTLD+1)** | `https://chunbae-front.app` | `https://chunbae-api.com` | cross-site | `true` | `None` | 환경변수 `COOKIE_SAMESITE=None` 주입 + CORS 엄격 관리 |

## 환경변수 / yml 설정

기본값은 `application.yml`에 박힘:

```yaml
cookie:
  refresh-token:
    secure: false                       # local default. prod yml이 true로 override
    same-site: ${COOKIE_SAMESITE:Lax}   # env 미설정 시 Lax
```

운영 cross-site 배치 시 deploy 환경에서:

```bash
COOKIE_SAMESITE=None
```

`application-prod.yml`은 `secure: true`를 강제하므로 `SameSite=None`과 정합. `CookieProperties` compact ctor가 `None + secure=false` 조합을 부팅 시 차단 → 사고 방지.

## 코드 측 정합 검증

`CookieProperties` record가 부팅 시 강제:

1. `name` / `path` 비어있으면 부팅 실패
2. `SameSite` enum 값(Lax/Strict/None) 외 yml에 들어오면 Spring 바인딩 실패 → 부팅 실패
3. **`SameSite=None` + `Secure=false` 조합 부팅 실패** — 브라우저 요구사항 강제. 운영 사고 차단.

추가로 운영 fronend/백엔드 도메인 정합(CORS `allowed-origins` vs same-site)은 코드로 판단 어려움 (origin이 same-site인지 cross-site인지 자동 판정 모호). 본 가이드 + reviewer 검토로 보강.

## CORS allowedOrigins와의 정합

- `SameSite=None`이면 cookie cross-site 전송 허용 → CORS `allowedOrigins`에 운영 프론트 origin 정확히 명시 + `allowCredentials=true` 필수
- 와일드카드(`*`)는 `allowCredentials=true`와 호환 불가 (브라우저 거부) → `CorsProperties` compact ctor가 이미 차단
- 운영에서 `CORS_ALLOWED_ORIGINS` 환경변수에 와일드카드 절대 금지 (이 정합은 `SecretValidator`가 prod 부팅 시 검증 — KAN-88)

## 운영 도메인 확정 시 체크리스트

운영 인프라(프론트/백엔드 도메인) 결정 후 배포 직전:

- [ ] 프론트엔드 운영 origin 확정 (HTTPS 필수)
- [ ] 백엔드 운영 API origin 확정
- [ ] 두 origin이 같은 eTLD+1인지 판단 → SameSite 정책 결정 (Lax 또는 None)
- [ ] `CORS_ALLOWED_ORIGINS` 환경변수에 운영 프론트 origin 명시 (콤마 구분, 와일드카드 금지)
- [ ] cross-site (Case B)이면 `COOKIE_SAMESITE=None` env 주입
- [ ] 운영 배포 후 브라우저 DevTools > Application > Cookies에서 `refreshToken` 쿠키가 저장되고 `SameSite` 컬럼이 정책대로 표시되는지 확인
- [ ] 로그인 후 `POST /api/v1/auth/reissue` 호출 시 cookie가 실제로 전송되는지 Network 탭으로 확인

## 검증된 브라우저 호환성

| 브라우저 | SameSite=Lax | SameSite=None+Secure |
|---|---|---|
| Chrome 80+ | ✅ | ✅ (Secure 필수) |
| Firefox 96+ | ✅ | ✅ (Secure 필수) |
| Safari 13.1+ | ✅ | ⚠️ ITP로 일부 동작 차이 (별도 검증 권장) |
| Edge Chromium | ✅ | ✅ |

Safari ITP(Intelligent Tracking Prevention)는 cross-site cookie를 더 엄격히 차단할 수 있음 → cross-site 배치 시 Safari에서 별도 검증 필요.

## 후속 / 범위 외

- 운영 도메인 자체 확정 — 비즈니스/인프라 결정 (본 슬라이스 범위 외)
- CSRF 토큰 발급 (SameSite=None 시 추가 방어층) — 별도 슬라이스
- ForwardedHeaderFilter / trusted proxy allowlist — KAN-65 후속 (별도 운영 작업)
- 다중 도메인 운영 (예: 한국/일본 별도 도메인) — 별도 multi-region PRD

## 참조

- KAN-23 S3 followup §1 (`docs/prd/KAN-23-s3-followup.md`) — 본 가이드로 흡수
- ADR 0002 Phase 2 ECS 전환 시 운영 도메인 결정 시점 (`docs/adr/0002-secret-injection-standard.md`)
- sa-docs/11 운영 보안 정책
- MDN: SameSite cookies (<https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite>)
