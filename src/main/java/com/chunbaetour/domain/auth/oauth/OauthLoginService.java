package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
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

    // KAN-105: 이메일 로그인(LoginService)과 동일 메트릭으로 OAuth 로그인도 관측 — 비밀번호 로그인만 보이고
    // 소셜 로그인은 안 보이는 사각지대 제거. outcome: success/needs_signup/role_mismatch/suspended.
    private static final String METRIC_LOGIN_ATTEMPT = "auth.login.attempt.total";

    private final List<OauthClient> oauthClients;
    private final AccountRepository accountRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final OauthSignupTicketIssuer ticketIssuer;
    private final MeterRegistry meterRegistry;
    private final SecurityAuditLogger auditLogger;

    public OauthLoginResult login(OauthProvider provider, String code, String redirectUri) {
        // resolve(미지원 provider) → OAUTH_PROVIDER_UNSUPPORTED, fetch(공급자 토큰/사용자 조회) → OAUTH_PROVIDER_ERROR.
        OauthClient client = resolve(provider);

        OauthUserInfo info;
        try {
            info = client.fetch(code, redirectUri);
        } catch (BusinessException e) {
            // 잘못된 code·redirectUri 불일치·공급자 장애·키 미설정 등 — OAuth 운영에서 가장 먼저 봐야 할 실패라
            // 메트릭/감사로그에 provider_error로 분리 기록(미지원 provider와 구분) (CR 반영).
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "provider_error").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, null, e.getErrorCode().getCode(),
                    Map.of("method", "oauth", "provider", provider.name(), "reasonDetail", "provider_fetch_failed"));
            throw e;
        }

        Account account = accountRepository
                .findByOauthProviderAndOauthId(provider, info.oauthId())
                .orElse(null);

        if (account == null) {
            // 우리 계정 없음 → 추가정보 입력 단계로. provider+oauthId를 담은 단기 티켓 발급.
            String ticket = ticketIssuer.issue(provider, info.oauthId());
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "needs_signup").increment();
            return OauthLoginResult.needSignup(ticket, info.email(), info.nickname());
        }

        // 소셜 로그인은 USER 전용 진입점(/api/v1/users/auth/oauth)이다. 승격된 MERCHANT/ADMIN 계정이 이 경로로
        // 자기 role 토큰을 발급받지 못하게 막는다 — 이메일 로그인(LoginService)의 endpoint별 role 경계와 동일 정책.
        if (account.getRole() != Role.USER) {
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "role_mismatch").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, account.getId(),
                    ErrorCode.ACCESS_DENIED.getCode(),
                    Map.of("method", "oauth", "provider", provider.name(),
                            "actualRole", account.getRole().name(), "reasonDetail", "oauth_role_not_user"));
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "suspended").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, account.getId(),
                    ErrorCode.ACCOUNT_SUSPENDED.getCode(),
                    Map.of("method", "oauth", "provider", provider.name(), "reasonDetail", "account_suspended"));
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        String accessToken = tokenIssuer.issueAccess(account.getId(), account.getRole(), account.getEmail());
        TokenWithId refresh = tokenIssuer.issueRefresh(account.getId());
        // 한 사용자 = 한 세션 모델 — 기존 refresh 덮어쓰기 (LoginService와 동일 정책).
        refreshTokenStore.save(account.getId(), refresh.tokenId(), jwtProperties.refreshTokenTtl());

        meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "success").increment();
        auditLogger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, account.getId(),
                Map.of("method", "oauth", "provider", provider.name(), "role", account.getRole().name()));

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
