package com.chunbaetour.domain.common.audit;

/**
 * 보안 감사 로그 이벤트 타입 카탈로그 (KAN-105 Epic KAN-64 S4).
 *
 * <p>{@code docs/operations/audit-log-catalog.md}와 동기 유지. 신규 타입 추가 시 카탈로그 문서도 함께 갱신해야 한다.
 *
 * <p>분류:
 * <ul>
 *   <li>LOGIN_* — 로그인 흐름 (LoginService)</li>
 *   <li>SIGNUP_* — 회원가입 (SignupService)</li>
 *   <li>LOGOUT — 명시적 로그아웃 (LogoutService)</li>
 *   <li>TOKEN_* — JWT 검증 분기 (JwtAuthenticationFilter)</li>
 *   <li>REFRESH_* — Refresh Token rotation (ReissueService)</li>
 *   <li>RATE_LIMIT_DENIED — Rate Limit 거부 (RateLimitFilter)</li>
 * </ul>
 *
 * <p>본 슬라이스는 인증 도메인 한정. 관리자 권한 변경 / 비밀번호 변경 / 회원 탈퇴 등은
 * 후속 도메인 PRD 도래 시 추가.
 */
public enum SecurityAuditEventType {

    /** 로그인 성공 — actorId/role/endpoint metadata 동반. */
    LOGIN_SUCCESS,
    /** 로그인 실패 — reason = AUTH_001(이메일/비밀번호 불일치) / AUTH_007(role mismatch) / AUTH_012(suspended). */
    LOGIN_FAILURE,

    /** 회원가입 성공 — role = USER. */
    SIGNUP_SUCCESS,
    /** 회원가입 실패 — reason = AUTH_008(email dup) / AUTH_009(nickname dup) / DB error. */
    SIGNUP_FAILURE,

    /** 로그아웃 — Access Token 블랙리스트 등록 + Refresh Token 삭제. */
    LOGOUT,

    /** JWT 만료 — reason = AUTH_002. 정상적 흐름이지만 빈도 모니터링 가치 있음. */
    TOKEN_EXPIRED,
    /** JWT 변조/Malformed — reason = AUTH_003. 공격 신호. */
    TOKEN_TAMPERED,
    /** 블랙리스트 토큰 재사용 시도 — reason = AUTH_013. 탈취 의심 토큰 활용 신호. */
    TOKEN_BLACKLISTED,

    /** Refresh Token rotation 성공. */
    REFRESH_ROTATED,
    /** Refresh Token 거부 — reason = AUTH_004(expired) / AUTH_005(invalid/missing). */
    REFRESH_REJECTED,

    /** Rate Limit 거부 — reason = AUTH_014. endpoint metadata 동반. */
    RATE_LIMIT_DENIED
}
