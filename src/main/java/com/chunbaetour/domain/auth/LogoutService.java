package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.AccessTokenBlacklist;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그아웃 흐름.
 *
 * <p>책임:
 * <ol>
 *   <li>Access Token의 tokenId를 {@link AccessTokenBlacklist}에 등록 (남은 만료 시간만큼).
 *       → 같은 Access Token으로 API 호출 시 즉시 거부됨 (AUTH_013).</li>
 *   <li>사용자의 Refresh Token을 Redis에서 삭제.
 *       → 같은 Refresh Cookie로 reissue 시도 시 거부됨 (AUTH_005).</li>
 * </ol>
 *
 * <p>설계 결정 — 부분 실패 허용:
 * <ul>
 *   <li>블랙리스트 등록과 Refresh 삭제는 별개의 Redis 키이며 트랜잭션이 없다.</li>
 *   <li>둘 중 하나가 실패해도 호출자에게 예외 전파(즉시 실패 응답). Redis는 일시 장애 시 Spring이 재시도하므로
 *       대부분의 경우 둘 다 성공한다.</li>
 *   <li>블랙리스트 등록만 성공한 경우라도 사용자의 Access Token은 즉시 무효화되어 보안 측면에서 부분적 효과는 있음.</li>
 *   <li>Refresh 삭제만 성공한 경우는 Access TTL(30분) 이후 자연 무효화.</li>
 * </ul>
 *
 * <p>{@link Clock}을 주입받는 이유: 잔여 TTL = {@code expiresAt - now}. 테스트에서 고정 시계로 계산을 검증하기 위해
 * Clock 추상화에 의존한다.
 */
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final AccessTokenBlacklist accessTokenBlacklist;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    /**
     * 로그아웃 처리.
     *
     * @param claims 인증 필터가 검증을 마친 Access Token 클레임. tokenId/expiresAt/userId를 모두 사용.
     */
    public void logout(AccessClaims claims) {
        // 잔여 TTL = exp - now. 시계 오차로 음수가 나오면 AccessTokenBlacklist.add가 자체적으로 스킵.
        Duration remainingTtl = Duration.between(clock.instant(), claims.expiresAt());

        // Access Token 즉시 무효화: 이후 같은 토큰으로 요청 시 JwtAuthenticationFilter가 AUTH_013 응답
        accessTokenBlacklist.add(claims.tokenId(), remainingTtl);

        // Refresh Token 키 제거: 이후 같은 Refresh Cookie로 reissue 시도 시 RefreshTokenStore.rotate가 false 반환 → AUTH_005
        refreshTokenStore.delete(claims.userId());
    }
}
