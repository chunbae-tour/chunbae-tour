package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "Pa$$w0rd1!";
    private static final String HASHED_PASSWORD = "hashed";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private LoginService loginService;

    private Account activeUser;

    @BeforeEach
    void setUp() {
        activeUser = activeAccount(1L, Role.USER, AccountStatus.ACTIVE);
    }

    @Test
    void login_success_returns_token_pair() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);
        given(tokenIssuer.issueAccess(1L, Role.USER, EMAIL)).willReturn("access");
        given(tokenIssuer.issueRefresh(1L)).willReturn(new TokenWithId("rid", "refresh"));

        TokenPair pair = loginService.login(EMAIL, RAW_PASSWORD, Role.USER);

        assertThat(pair.accessToken()).isEqualTo("access");
        assertThat(pair.refreshToken()).isEqualTo("refresh");
        assertThat(pair.refreshTokenId()).isEqualTo("rid");
        assertThat(pair.role()).isEqualTo(Role.USER);
    }

    @Test
    void login_lowercases_email_before_lookup() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);
        given(tokenIssuer.issueAccess(eq(1L), eq(Role.USER), eq(EMAIL))).willReturn("access");
        given(tokenIssuer.issueRefresh(anyLong())).willReturn(new TokenWithId("rid", "refresh"));

        loginService.login("USER@Example.com", RAW_PASSWORD, Role.USER);
    }

    @Test
    void login_with_nonexistent_email_throws_AUTH_001() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void login_with_wrong_password_throws_AUTH_001() {
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(false);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void login_with_suspended_account_throws_AUTH_012() {
        Account suspended = activeAccount(2L, Role.USER, AccountStatus.SUSPENDED);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(suspended));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void login_with_role_mismatch_throws_AUTH_007() {
        Account admin = activeAccount(3L, Role.ADMIN, AccountStatus.ACTIVE);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(admin));
        given(passwordHasher.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(EMAIL, RAW_PASSWORD, Role.USER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    private static Account activeAccount(long id, Role role, AccountStatus status) {
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
