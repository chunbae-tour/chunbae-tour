package com.chunbaetour.domain.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.LogoutTokenStore;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
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
 *   <li>logoutTokenStore가 정확한 userId + tokenId + 남은 TTL로 호출되는가</li>
 *   <li>만료된 토큰(잔여 TTL ≤ 0)도 저장소에 위임되는가</li>
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
    private LogoutTokenStore logoutTokenStore;

    @Mock
    private SecurityAuditLogger auditLogger;

    private LogoutService logoutService;

    private void initService() {
        // @InjectMocks 대신 명시적으로 만든다 — Clock을 컨트롤하기 위해.
        logoutService = new LogoutService(logoutTokenStore, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), auditLogger);
    }

    @Test
    void logout_registers_blacklist_with_remaining_ttl_and_deletes_refresh() {
        initService();
        Instant expiresAt = FIXED_NOW.plus(Duration.ofMinutes(10));
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID, expiresAt);

        logoutService.logout(claims);

        // Access blacklist 등록과 Refresh 삭제를 하나의 저장소 호출로 위임한다.
        verify(logoutTokenStore).invalidate(USER_ID, TOKEN_ID, Duration.ofMinutes(10));
    }

    @Test
    void logout_with_already_expired_token_still_deletes_refresh() {
        // 만료된 토큰이 인증 필터를 통과해 logout까지 오는 경우는 거의 없지만, 시계 오차/race로 가능.
        // 이 경우에도 Refresh는 삭제되어야 하므로 저장소에 그대로 위임한다.
        initService();
        Instant expiresAt = FIXED_NOW.minus(Duration.ofSeconds(5));
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID, expiresAt);

        logoutService.logout(claims);

        verify(logoutTokenStore).invalidate(eq(USER_ID), eq(TOKEN_ID), any(Duration.class));
    }

    @Test
    void logout_does_not_invoke_other_users_refresh_delete() {
        // userId가 잘못 전달되면 다른 사용자의 Refresh가 지워지므로 인자 검증이 핵심.
        initService();
        AccessClaims claims = new AccessClaims(USER_ID, Role.USER, "u@e.c", TOKEN_ID,
                FIXED_NOW.plus(Duration.ofMinutes(5)));

        logoutService.logout(claims);

        verify(logoutTokenStore).invalidate(eq(USER_ID), eq(TOKEN_ID), any(Duration.class));
        verify(logoutTokenStore, never()).invalidate(eq(99L), anyString(), any(Duration.class));
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

        verify(logoutTokenStore, times(2)).invalidate(eq(USER_ID), eq(TOKEN_ID), any(Duration.class));
    }
}
