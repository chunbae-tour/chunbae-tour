package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.LogoutTokenStore;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그아웃 흐름.
 *
 * <p>책임:
 * <ol>
 *   <li>{@link LogoutTokenStore}에 로그아웃 상태 변경을 위임한다.</li>
 *   <li>Redis Lua script 안에서 Access Token의 tokenId를 blacklist에 등록하고 Refresh Token 키를 삭제한다.</li>
 * </ol>
 *
 * <p>설계 결정 — 원자적 로그아웃 처리:
 * <ul>
 *   <li>Access blacklist 등록과 Refresh 삭제는 같은 Redis 인스턴스에서 Lua script 한 번으로 수행한다.</li>
 *   <li>Redis 명령이 실패하면 상태 변경을 부분 성공으로 간주하지 않고 예외를 전파해 실패 응답으로 끝낸다.</li>
 *   <li>잔여 Access TTL이 0 이하이면 blacklist 등록은 생략하지만 Refresh 삭제는 같은 script에서 수행한다.</li>
 * </ul>
 *
 * <p>{@link Clock}을 주입받는 이유: 잔여 TTL = {@code expiresAt - now}. 테스트에서 고정 시계로 계산을 검증하기 위해
 * Clock 추상화에 의존한다.
 */
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final LogoutTokenStore logoutTokenStore;
    private final Clock clock;

    /**
     * 로그아웃 처리.
     *
     * @param claims 인증 필터가 검증을 마친 Access Token 클레임. tokenId/expiresAt/userId를 모두 사용.
     */
    public void logout(AccessClaims claims) {
        // 잔여 TTL = exp - now. 0 이하이면 blacklist 등록은 생략하고 Refresh 삭제만 수행한다.
        Duration remainingTtl = Duration.between(clock.instant(), claims.expiresAt());

        // Access blacklist 등록 + Refresh 삭제를 Lua script 한 번으로 처리해 부분 로그아웃 상태를 방지한다.
        logoutTokenStore.invalidate(claims.userId(), claims.tokenId(), remainingTtl);
    }
}
