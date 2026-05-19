package com.chunbaetour.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.auth.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenIssuerTest {

    private static final String SECRET = "test-only-secret-32-bytes-min-xxxxxx";
    private static final String OTHER_SECRET = "totally-different-secret-32-bytes-yyyy";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final Instant FIXED_NOW = Instant.parse("2026-05-19T10:00:00Z");

    private final JwtProperties props = new JwtProperties(SECRET, ACCESS_TTL, REFRESH_TTL);
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final TokenIssuer issuer = new TokenIssuer(props, fixedClock);

    @Test
    void issueAccess_then_verifyAccess_returns_original_claims() {
        String token = issuer.issueAccess(42L, Role.USER, "user@example.com");

        AccessClaims claims = issuer.verifyAccess(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(Role.USER);
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.tokenId()).isNotBlank();
    }

    @Test
    void issueRefresh_then_verifyRefresh_returns_original_claims() {
        TokenWithId issued = issuer.issueRefresh(42L);

        RefreshClaims claims = issuer.verifyRefresh(issued.token());

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.tokenId()).isEqualTo(issued.tokenId());
    }

    @Test
    void verifyAccess_with_expired_token_throws_ExpiredJwtException() {
        TokenIssuer pastIssuer = new TokenIssuer(props, Clock.fixed(FIXED_NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        String expiredToken = pastIssuer.issueAccess(1L, Role.USER, "x@y.z");

        assertThatThrownBy(() -> issuer.verifyAccess(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void verifyAccess_with_tampered_token_throws_JwtException() {
        String token = issuer.issueAccess(1L, Role.USER, "x@y.z");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> issuer.verifyAccess(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyAccess_with_token_signed_by_other_key_throws_JwtException() {
        TokenIssuer otherIssuer = new TokenIssuer(
                new JwtProperties(OTHER_SECRET, ACCESS_TTL, REFRESH_TTL),
                fixedClock);
        String foreignToken = otherIssuer.issueAccess(1L, Role.USER, "x@y.z");

        assertThatThrownBy(() -> issuer.verifyAccess(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyAccess_rejects_refresh_token() {
        TokenWithId refresh = issuer.issueRefresh(1L);

        assertThatThrownBy(() -> issuer.verifyAccess(refresh.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRefresh_rejects_access_token() {
        String access = issuer.issueAccess(1L, Role.USER, "x@y.z");

        assertThatThrownBy(() -> issuer.verifyRefresh(access))
                .isInstanceOf(JwtException.class);
    }
}
