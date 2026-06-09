package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 1단계 — 인가코드로 공급자 사용자 식별 후, 기존 계정이면 로그인(JWT 발급), 신규면 가입 티켓 발급.
 *
 * <p>외부 HTTP 호출(공급자 토큰/사용자 조회)을 포함하므로 DB 트랜잭션으로 감싸지 않는다(커넥션을 외부 I/O
 * 동안 점유하지 않기 위함). 계정 조회는 단건 read, refresh 저장은 Redis라 트랜잭션 무관.
 */
@Service
@RequiredArgsConstructor
public class OauthLoginService {

    private final List<OauthClient> oauthClients;
    private final AccountRepository accountRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final OauthSignupTicketIssuer ticketIssuer;

    public OauthLoginResult login(OauthProvider provider, String code, String redirectUri) {
        OauthUserInfo info = resolve(provider).fetch(code, redirectUri);

        Account account = accountRepository
                .findByOauthProviderAndOauthId(provider, info.oauthId())
                .orElse(null);

        if (account == null) {
            // 우리 계정 없음 → 추가정보 입력 단계로. provider+oauthId를 담은 단기 티켓 발급.
            String ticket = ticketIssuer.issue(provider, info.oauthId());
            return OauthLoginResult.needSignup(ticket, info.email(), info.nickname());
        }

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        String accessToken = tokenIssuer.issueAccess(account.getId(), account.getRole(), account.getEmail());
        TokenWithId refresh = tokenIssuer.issueRefresh(account.getId());
        // 한 사용자 = 한 세션 모델 — 기존 refresh 덮어쓰기 (LoginService와 동일 정책).
        refreshTokenStore.save(account.getId(), refresh.tokenId(), jwtProperties.refreshTokenTtl());

        return OauthLoginResult.loggedIn(
                new TokenPair(accessToken, refresh.token(), refresh.tokenId(), account.getRole()));
    }

    private OauthClient resolve(OauthProvider provider) {
        return oauthClients.stream()
                .filter(client -> client.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED));
    }
}
