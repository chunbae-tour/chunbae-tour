package com.chunbaetour.domain.auth.jwt;

import com.chunbaetour.domain.auth.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TokenIssuer {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_ID = "tid";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    @Autowired
    public TokenIssuer(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    TokenIssuer(JwtProperties props, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.accessTokenTtl();
        this.refreshTtl = props.refreshTokenTtl();
        this.clock = clock;
    }

    public String issueAccess(long userId, Role role, String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_ID, UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    public TokenWithId issueRefresh(long userId) {
        Instant now = clock.instant();
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_TOKEN_ID, tokenId)
                .signWith(signingKey)
                .compact();
        return new TokenWithId(tokenId, token);
    }

    public AccessClaims verifyAccess(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_ACCESS);
        long userId = Long.parseLong(claims.getSubject());
        Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
        String email = claims.get(CLAIM_EMAIL, String.class);
        String tokenId = claims.get(CLAIM_TOKEN_ID, String.class);
        return new AccessClaims(userId, role, email, tokenId);
    }

    public RefreshClaims verifyRefresh(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_REFRESH);
        long userId = Long.parseLong(claims.getSubject());
        String tokenId = claims.get(CLAIM_TOKEN_ID, String.class);
        return new RefreshClaims(userId, tokenId);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void requireType(Claims claims, String expected) {
        Object actual = claims.get(CLAIM_TYPE);
        if (!expected.equals(actual)) {
            throw new JwtException("토큰 타입이 일치하지 않습니다. expected=" + expected + ", actual=" + actual);
        }
    }
}
