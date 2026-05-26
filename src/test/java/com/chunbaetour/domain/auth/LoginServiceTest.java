package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LoginService} 단위 테스트.
 *
 * <p>S3 변경 사항:
 * <ul>
 *   <li>{@link RefreshTokenStore} mock 추가 — save 호출 검증</li>
 *   <li>{@link JwtProperties} mock 추가 — refreshTokenTtl 주입 필요</li>
 *   <li>성공 경로에서 {@code store.save(userId, tokenId, ttl)} 정확히 호출되는지 명시적 verify</li>
 *   <li>실패 경로(비밀번호 불일치/정지/role mismatch)에서는 store.save가 절대 호출되지 않아야 함 검증</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "Pa$$w0rd1!";
    private static final String HASHED_PASSWORD = "hashed";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private JwtProperties jwtProperties;

    /**
     * KAN-104 메트릭 검증을 위해 in-memory {@link SimpleMeterRegistry} 주입.
     * {@code @Mock}을 쓰면 {@code meterRegistry.counter(...)} 체인이 NPE 발생 — 실제 구현체 사용이 단순/안전.
     */
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private LoginService loginService;

    private Account activeUser;

    @BeforeEach
    void setUp() {
        activeUser = accountWith(1L, Role.USER, AccountStatus.ACTIVE);
    }

    @Test
    void login_success_returns_token_pair_and_stores_refresh_in_redis() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);
        given(tokenIssuer.issueAccess(1L, Role.USER, EMAIL)).willReturn("access");
        given(tokenIssuer.issueRefresh(1L)).willReturn(new TokenWithId("rid", "refresh"));
        given(jwtProperties.refreshTokenTtl()).willReturn(REFRESH_TTL);

        TokenPair pair = loginService.login(EMAIL, RAW_PASSWORD, Role.USER);

        // Body 응답용 토큰 쌍이 그대로 반환되는지
        assertThat(pair.accessToken()).isEqualTo("access");
        assertThat(pair.refreshToken()).isEqualTo("refresh");
        assertThat(pair.refreshTokenId()).isEqualTo("rid");
        assertThat(pair.role()).isEqualTo(Role.USER);

        // Refresh가 Redis에 저장돼야 reissue 흐름에서 조회 가능 (PRD 핵심 동작)
        verify(refreshTokenStore).save(1L, "rid", REFRESH_TTL);
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    void login_lowercases_email_before_lookup() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);
        given(tokenIssuer.issueAccess(eq(1L), eq(Role.USER), eq(EMAIL))).willReturn("access");
        given(tokenIssuer.issueRefresh(anyLong())).willReturn(new TokenWithId("rid", "refresh"));
        given(jwtProperties.refreshTokenTtl()).willReturn(REFRESH_TTL);

        loginService.login("USER@Example.com", RAW_PASSWORD, Role.USER);

        // 입력은 대문자 섞임 → 조회는 lowercase로
        verify(accountRepository).findByEmail(EMAIL);
    }

    @Test
    void login_with_nonexistent_email_throws_AUTH_001_and_does_not_save_refresh() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);

        // 실패 시 어떤 tokenId/TTL 조합으로도 Redis 저장이 일어나면 안 된다.
        verifyRefreshWasNotSaved();
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "invalid_password").count())
                .isEqualTo(1.0);
    }

    @Test
    void login_with_wrong_password_throws_AUTH_001() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(false);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);

        verifyRefreshWasNotSaved();
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "invalid_password").count())
                .isEqualTo(1.0);
    }

    @Test
    void login_with_suspended_account_throws_AUTH_012() {
        Account suspended = accountWith(2L, Role.USER, AccountStatus.SUSPENDED);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(suspended));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verifyRefreshWasNotSaved();
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "suspended").count())
                .isEqualTo(1.0);
    }

    @Test
    void login_with_role_mismatch_throws_AUTH_007() {
        Account admin = accountWith(3L, Role.ADMIN, AccountStatus.ACTIVE);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(admin));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verifyRefreshWasNotSaved();
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "role_mismatch").count())
                .isEqualTo(1.0);
    }

    /**
     * 보안 회귀 방지: SUSPENDED 상태의 다른 role 계정으로 로그인 시도 시 SUSPENDED 사실이 노출되면 안 된다.
     *
     * <p>이전 순서(SUSPENDED → role)는 {@code AUTH_012}가 먼저 응답되어 "이 이메일은 다른 role의 정지 계정이다"라는
     * 정보가 page 호출자에게 누설되는 oracle이었다. 변경 후(role → SUSPENDED)는 {@code AUTH_007}이 먼저
     * 응답되어 정지 사실이 가려진다.
     */
    @Test
    void login_with_role_mismatch_and_suspended_status_throws_AUTH_007_not_AUTH_012() {
        // ADMIN + SUSPENDED 계정을 USER endpoint로 로그인 시도
        Account suspendedAdmin = accountWith(4L, Role.ADMIN, AccountStatus.SUSPENDED);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(suspendedAdmin));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                // 정지 사실(AUTH_012) 대신 권한 부족(AUTH_007)이 먼저 노출되어야 oracle 차단
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verifyRefreshWasNotSaved();
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "role_mismatch").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("auth.login.attempt.total", "outcome", "suspended").count())
                .isEqualTo(0.0);
    }

    private void verifyRefreshWasNotSaved() {
        verify(refreshTokenStore, never()).save(anyLong(), anyString(), any(Duration.class));
    }

    /**
     * 테스트 헬퍼: Account 엔티티의 사용자 정의 ID/role/status를 reflect로 주입.
     * 정식 setter가 없으므로 (불변 도메인 원칙) 테스트 한정 reflect 사용.
     */
    private static Account accountWith(long id, Role role, AccountStatus status) {
        Account account = Account.registerUser(EMAIL, HASHED_PASSWORD, "tester");
        writeField(account, "id", id);
        writeField(account, "role", role);
        writeField(account, "status", status);
        return account;
    }

    private static void writeField(Account account, String name, Object value) {
        try {
            Field field = Account.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(account, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
