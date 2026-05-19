package com.chunbaetour.domain.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.AccessTokenBlacklist;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LogoutService} 단위 테스트.
 *
 * <p>외부 동작 검증(PRD의 좋은 테스트 기준):
 * <ul>
 *   <li>blacklist.add가 정확한 tokenId + 남은 TTL로 호출되는가</li>
 *   <li>refreshTokenStore.delete가 정확한 userId로 호출되는가</li>
 *   <li>만료된 토큰(잔여 TTL ≤ 0)일 때 blacklist 등록은 어떻게 처리되는가</li>
 * </ul>
 *
 * <p>고정 시계({@code Clock.fixed})를 주입하여 잔여 TTL 계산이 결정적이게 한다.
 */
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-19T10:00:00Z");
    private static final long USER_ID = 42L;
    private static final String TOKEN_ID = "token-uuid";

    @Mock
    private AccessTokenBlacklist accessTokenBlacklist;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private LogoutService logoutService;

    private void initService() {
        // @InjectMocks 대신 명시적으로 만든다 — Clock을 컨트롤하기 위해.
        logoutService = new LogoutService(accessTokenBlacklist, refreshTokenStore, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void logout_registers_blacklist_with_remaining_ttl_and_deletes_refresh() {
        initService();
        Instant expiresAt = FIXED_NOW.plus(Duration.ofMinutes(10));
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID, expiresAt);

        logoutService.logout(claims);

        // 정확히 잔여 TTL(10분)로 블랙리스트 등록
        verify(accessTokenBlacklist).add(TOKEN_ID, Duration.ofMinutes(10));
        // Refresh도 함께 삭제
        verify(refreshTokenStore).delete(USER_ID);
    }

    @Test
    void logout_with_already_expired_token_still_deletes_refresh() {
        // 만료된 토큰이 인증 필터를 통과해 logout까지 오는 경우는 거의 없지만, 시계 오차/race로 가능.
        // 이 경우에도 Refresh는 삭제되어야 함 (계정 안전 차원).
        // 블랙리스트는 AccessTokenBlacklist 내부에서 음수 TTL 스킵 분기 처리.
        initService();
        Instant expiresAt = FIXED_NOW.minus(Duration.ofSeconds(5));
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID, expiresAt);

        logoutService.logout(claims);

        // 음수 TTL로 호출되어도 blacklist 내부에서 스킵된다 (AccessTokenBlacklist 테스트에서 별도 검증).
        // 여기서는 LogoutService 책임만 검증: add를 정확한 인자로 한 번은 호출.
        verify(accessTokenBlacklist).add(eq(TOKEN_ID), any(Duration.class));
        verify(refreshTokenStore).delete(USER_ID);
    }

    @Test
    void logout_does_not_invoke_other_users_refresh_delete() {
        // userId가 잘못 전달되면 다른 사용자의 Refresh가 지워지므로 인자 검증이 핵심.
        initService();
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID,
                FIXED_NOW.plus(Duration.ofMinutes(5)));

        logoutService.logout(claims);

        verify(refreshTokenStore).delete(USER_ID);
        verify(refreshTokenStore, never()).delete(99L);
        verify(accessTokenBlacklist, never()).add(anyString(), eq(Duration.ZERO));
    }

    /**
     * 같은 클레임으로 두 번 호출되면 두 번 모두 부수 효과가 실행되어야 한다 (idempotency가 호출자 책임).
     */
    @Test
    void logout_invokes_delete_exactly_once_per_call() {
        initService();
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID,
                FIXED_NOW.plus(Duration.ofMinutes(5)));

        logoutService.logout(claims);
        logoutService.logout(claims);

        verify(refreshTokenStore, org.mockito.Mockito.times(2)).delete(USER_ID);
        verify(accessTokenBlacklist, org.mockito.Mockito.times(2)).add(eq(TOKEN_ID), any(Duration.class));
    }
}
