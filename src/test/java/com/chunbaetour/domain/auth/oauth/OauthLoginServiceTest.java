package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OauthLoginService} 단위 테스트 — 기존 계정 로그인 / 신규 가입 티켓 / 정지 / 미지원 공급자.
 */
@ExtendWith(MockitoExtension.class)
class OauthLoginServiceTest {

    @Mock private OauthClient kakaoClient;
    @Mock private AccountRepository accountRepository;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private JwtProperties jwtProperties;
    @Mock private OauthSignupTicketIssuer ticketIssuer;

    private OauthLoginService service;

    @BeforeEach
    void setUp() {
        service = new OauthLoginService(
                List.of(kakaoClient), accountRepository, tokenIssuer, refreshTokenStore, jwtProperties, ticketIssuer);
        when(kakaoClient.provider()).thenReturn(OauthProvider.KAKAO);
    }

    @DisplayName("기존 소셜 계정 → 토큰 발급(로그인)")
    @Test
    void existingAccountLogsIn() {
        when(kakaoClient.fetch(any(), any()))
                .thenReturn(new OauthUserInfo(OauthProvider.KAKAO, "oid-1", "user@example.com", "춘배"));

        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getId()).thenReturn(1L);
        when(account.getRole()).thenReturn(Role.USER);
        when(account.getEmail()).thenReturn("user@example.com");
        when(accountRepository.findByOauthProviderAndOauthId(OauthProvider.KAKAO, "oid-1"))
                .thenReturn(Optional.of(account));

        when(tokenIssuer.issueAccess(1L, Role.USER, "user@example.com")).thenReturn("access-token");
        when(tokenIssuer.issueRefresh(1L)).thenReturn(new TokenWithId("tid-1", "refresh-token"));
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));

        OauthLoginResult result = service.login(OauthProvider.KAKAO, "code", "https://app/callback");

        assertThat(result.needSignup()).isFalse();
        assertThat(result.tokenPair().accessToken()).isEqualTo("access-token");
        assertThat(result.tokenPair().role()).isEqualTo(Role.USER);
        verify(refreshTokenStore).save(eq(1L), eq("tid-1"), any(Duration.class));
    }

    @DisplayName("미가입 소셜 계정 → 가입 티켓 발급(needSignup)")
    @Test
    void newAccountNeedsSignup() {
        when(kakaoClient.fetch(any(), any()))
                .thenReturn(new OauthUserInfo(OauthProvider.KAKAO, "oid-2", "new@example.com", "newbie"));
        when(accountRepository.findByOauthProviderAndOauthId(OauthProvider.KAKAO, "oid-2"))
                .thenReturn(Optional.empty());
        when(ticketIssuer.issue(OauthProvider.KAKAO, "oid-2")).thenReturn("signup-ticket");

        OauthLoginResult result = service.login(OauthProvider.KAKAO, "code", "https://app/callback");

        assertThat(result.needSignup()).isTrue();
        assertThat(result.signupTicket()).isEqualTo("signup-ticket");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.nickname()).isEqualTo("newbie");
        verify(tokenIssuer, never()).issueRefresh(anyLong());
    }

    @DisplayName("정지된 계정 → ACCOUNT_SUSPENDED")
    @Test
    void suspendedAccountRejected() {
        when(kakaoClient.fetch(any(), any()))
                .thenReturn(new OauthUserInfo(OauthProvider.KAKAO, "oid-3", "susp@example.com", "susp"));
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.SUSPENDED);
        when(accountRepository.findByOauthProviderAndOauthId(OauthProvider.KAKAO, "oid-3"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(OauthProvider.KAKAO, "code", "https://app/callback"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @DisplayName("미지원 공급자 → OAUTH_PROVIDER_UNSUPPORTED (등록된 클라이언트 없음)")
    @Test
    void unsupportedProviderRejected() {
        assertThatThrownBy(() -> service.login(OauthProvider.NAVER, "code", "https://app/callback"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
    }
}
