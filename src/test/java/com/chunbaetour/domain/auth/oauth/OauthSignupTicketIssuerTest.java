package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OauthSignupTicketIssuer} 단위 테스트 — 정상 라운드트립 + 위조/손상 티켓이 NPE가 아닌
 * OAUTH_SIGNUP_TICKET_INVALID(401)로 변환되는지(자체검증 워크플로우 적발 버그 회귀 가드).
 */
class OauthSignupTicketIssuerTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-0123456789";
    private final JwtProperties props =
            new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(7));
    private final OauthSignupTicketIssuer issuer = new OauthSignupTicketIssuer(props);
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @DisplayName("정상 발급→검증 라운드트립 (공급자 이메일 포함)")
    @Test
    void issueThenVerifyRoundTrip() {
        String ticket = issuer.issue(OauthProvider.KAKAO, "oid-123", "verified@kakao.com");

        OauthSignupTicket parsed = issuer.verify(ticket);

        assertThat(parsed.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(parsed.oauthId()).isEqualTo("oid-123");
        assertThat(parsed.email()).isEqualTo("verified@kakao.com");
    }

    @DisplayName("공급자 이메일 없이 발급(null) → 검증 시 email=null (가입 단계가 거부 처리)")
    @Test
    void issueWithoutEmail() {
        String ticket = issuer.issue(OauthProvider.NAVER, "oid-999", null);

        OauthSignupTicket parsed = issuer.verify(ticket);

        assertThat(parsed.provider()).isEqualTo(OauthProvider.NAVER);
        assertThat(parsed.oauthId()).isEqualTo("oid-999");
        assertThat(parsed.email()).isNull();
    }

    @DisplayName("provider 클레임 없는 티켓 → NPE 아닌 OAUTH_SIGNUP_TICKET_INVALID(401)")
    @Test
    void missingProviderClaimRejectedCleanly() {
        // 같은 키로 서명하되 provider 클레임을 누락한 위조/손상 티켓.
        String tampered = Jwts.builder()
                .subject("oid-123")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .claim("typ", "oauth_signup")
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> issuer.verify(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
    }

    @DisplayName("타입 불일치(access 토큰류) → OAUTH_SIGNUP_TICKET_INVALID")
    @Test
    void wrongTypeRejected() {
        String wrongType = Jwts.builder()
                .subject("oid-123")
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .claim("typ", "access")
                .claim("provider", "KAKAO")
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> issuer.verify(wrongType))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
    }

    @DisplayName("서명/형식 깨진 티켓 → OAUTH_SIGNUP_TICKET_INVALID")
    @Test
    void garbageTicketRejected() {
        assertThatThrownBy(() -> issuer.verify("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_SIGNUP_TICKET_INVALID);
    }
}
