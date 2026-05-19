package com.chunbaetour.domain.auth.jwt;

import com.chunbaetour.domain.auth.Role;
import java.time.Instant;

/**
 * 검증된 Access Token의 클레임 묶음.
 *
 * <p>{@link TokenIssuer#verifyAccess}가 만들어 호출자에게 반환한다. JJWT의 내부 {@code Claims} 타입을
 * 노출하지 않고 도메인 친화적 record로 캡슐화하여 라이브러리 교체/업그레이드에 강건하게 한다.
 *
 * @param userId    JWT subject에서 파싱한 사용자 ID
 * @param role      JWT {@code role} 클레임
 * @param email     JWT {@code email} 클레임 — 응답 캐싱/감사 로그 용도
 * @param tokenId   JWT {@code tid} 클레임 (UUID) — Refresh rotation 추적, 블랙리스트 등록 키로 사용
 * @param expiresAt JWT {@code exp} 클레임의 Instant 표현 — 블랙리스트 TTL 계산({@code expiresAt - now})에 필요.
 *                  (S4에서 추가된 필드. 블랙리스트가 만료된 토큰까지 무한 보유하지 않도록 정확한 잔여 TTL을 알아야 함.)
 */
public record AccessClaims(long userId, Role role, String email, String tokenId, Instant expiresAt) {
}
