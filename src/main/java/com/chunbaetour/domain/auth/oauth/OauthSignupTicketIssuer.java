package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 소셜 신규 가입 티켓(단기 서명 JWT) 발급/검증.
 *
 * <p>소셜 인증을 통과했지만 우리 계정이 없는 사용자에게 {@code provider + oauthId}를 담은 10분짜리
 * 서명 토큰을 발급한다. 추가정보 입력 단계({@code POST /oauth/signup})에서 이 티켓을 제출해야 계정이
 * 생성되며, 서명 검증으로 "임의 oauthId 위조 가입"을 막는다. 서명키는 기존 JWT secret을 재사용한다.
 *
 * <p>access/refresh 토큰과 {@code typ} 클레임({@code oauth_signup})으로 구분해 교차 사용을 차단한다.
 */
@Component
public class OauthSignupTicketIssuer {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_OAUTH_SIGNUP = "oauth_signup";
    private static final String CLAIM_PROVIDER = "provider";
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);

    private final SecretKey signingKey;
    private final Clock clock;

    @Autowired
    public OauthSignupTicketIssuer(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    OauthSignupTicketIssuer(JwtProperties props, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    /**
     * 신규 소셜 사용자에게 단기 가입 티켓 발급.
     *
     * @param provider 소셜 공급자
     * @param oauthId  공급자 사용자 식별자 (subject)
     */
    public String issue(OauthProvider provider, String oauthId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(oauthId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TICKET_TTL)))
                .claim(CLAIM_TYPE, TYPE_OAUTH_SIGNUP)
                .claim(CLAIM_PROVIDER, provider.name())
                .signWith(signingKey)
                .compact();
    }

    /**
     * 가입 티켓 검증 — 서명/만료/타입 확인 후 {@link OauthSignupTicket} 반환.
     *
     * @throws BusinessException 서명 오류·만료·타입 불일치 등 ({@link ErrorCode#OAUTH_SIGNUP_TICKET_INVALID})
     */
    public OauthSignupTicket verify(String ticket) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(ticket)
                    .getPayload();
            if (!TYPE_OAUTH_SIGNUP.equals(claims.get(CLAIM_TYPE))) {
                throw new BusinessException(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
            }
            OauthProvider provider = OauthProvider.valueOf(claims.get(CLAIM_PROVIDER, String.class));
            String oauthId = claims.getSubject();
            if (oauthId == null || oauthId.isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
            }
            return new OauthSignupTicket(provider, oauthId);
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
        }
    }
}
