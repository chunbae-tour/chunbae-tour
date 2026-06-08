package com.chunbaetour.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.auth.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
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
        // S4: 블랙리스트 TTL 계산을 위해 expiresAt이 정확히 issuedAt + access-token-ttl이어야 함.
        // JWT exp는 초 정밀도라 millis 제거 후 비교.
        assertThat(claims.expiresAt()).isEqualTo(FIXED_NOW.plus(ACCESS_TTL).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
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
    void verifyAccess_with_missing_required_claim_throws_JwtException() {
        String missingRole = signedAccess("1", null, "x@y.z", "tid");
        String missingEmail = signedAccess("1", Role.USER.name(), null, "tid");
        String missingTokenId = signedAccess("1", Role.USER.name(), "x@y.z", null);

        assertThatThrownBy(() -> issuer.verifyAccess(missingRole))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> issuer.verifyAccess(missingEmail))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> issuer.verifyAccess(missingTokenId))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyAccess_with_malformed_signed_claim_throws_JwtException() {
        String badSubject = signedAccess("not-a-number", Role.USER.name(), "x@y.z", "tid");
        String badRole = signedAccess("1", "UNKNOWN", "x@y.z", "tid");

        assertThatThrownBy(() -> issuer.verifyAccess(badSubject))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> issuer.verifyAccess(badRole))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRefresh_rejects_access_token() {
        String access = issuer.issueAccess(1L, Role.USER, "x@y.z");

        assertThatThrownBy(() -> issuer.verifyRefresh(access))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRefresh_with_missing_or_malformed_claim_throws_JwtException() {
        String missingTokenId = signedRefresh("1", null);
        String badSubject = signedRefresh("not-a-number", "tid");

        assertThatThrownBy(() -> issuer.verifyRefresh(missingTokenId))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> issuer.verifyRefresh(badSubject))
                .isInstanceOf(JwtException.class);
    }

    private static String signedAccess(String subject, String role, String email, String tokenId) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(ACCESS_TTL)))
                .claim("typ", "access");
        if (role != null) {
            builder.claim("role", role);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        if (tokenId != null) {
            builder.claim("tid", tokenId);
        }
        return builder.signWith(signingKey()).compact();
    }

    private static String signedRefresh(String subject, String tokenId) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(REFRESH_TTL)))
                .claim("typ", "refresh");
        if (tokenId != null) {
            builder.claim("tid", tokenId);
        }
        return builder.signWith(signingKey()).compact();
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
