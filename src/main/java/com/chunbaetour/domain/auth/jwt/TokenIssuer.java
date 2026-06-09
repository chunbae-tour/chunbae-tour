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
    private static final String CLAIM_ITEM_ID = "itemId";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_ITEM_QR = "item_qr";
    private static final Duration ITEM_QR_TTL = Duration.ofMinutes(5);

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

    public IssuedItemQr issueItemQr(long userId, long itemId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ITEM_QR_TTL);
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TYPE_ITEM_QR)
                .claim(CLAIM_ITEM_ID, itemId)
                .signWith(signingKey)
                .compact();
        // 발급 시점에 만료시각을 이미 계산했으므로 토큰과 함께 반환 — 호출부가 재파싱하지 않도록 한다.
        return new IssuedItemQr(token, expiresAt);
    }

    public AccessClaims verifyAccess(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_ACCESS);
        String roleValue = requireStringClaim(claims, CLAIM_ROLE);
        String email = requireStringClaim(claims, CLAIM_EMAIL);
        String tokenId = requireStringClaim(claims, CLAIM_TOKEN_ID);
        try {
            long userId = Long.parseLong(claims.getSubject());
            Role role = Role.valueOf(roleValue);
            // exp 클레임을 Instant로 변환. S4 블랙리스트가 정확한 잔여 TTL로 등록되어
            // 만료된 토큰이 영구히 Redis에 누적되지 않도록 한다.
            Instant expiresAt = claims.getExpiration().toInstant();
            return new AccessClaims(userId, role, email, tokenId, expiresAt);
        } catch (RuntimeException e) {
            throw new JwtException("토큰 클레임이 유효하지 않습니다.", e);
        }
    }

    public RefreshClaims verifyRefresh(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_REFRESH);
        String tokenId = requireStringClaim(claims, CLAIM_TOKEN_ID);
        try {
            long userId = Long.parseLong(claims.getSubject());
            return new RefreshClaims(userId, tokenId);
        } catch (RuntimeException e) {
            throw new JwtException("토큰 클레임이 유효하지 않습니다.", e);
        }
    }

    public ItemQrClaims verifyItemQr(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_ITEM_QR);
        try {
            long userId = Long.parseLong(claims.getSubject());
            Long itemId = claims.get(CLAIM_ITEM_ID, Long.class);
            if (itemId == null) {
                throw new JwtException("아이템 QR 토큰의 itemId 클레임이 누락되었습니다.");
            }
            return new ItemQrClaims(userId, itemId, claims.getExpiration().toInstant());
        } catch (JwtException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JwtException("아이템 QR 토큰 페이로드가 유효하지 않습니다.", e);
        }
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

    private String requireStringClaim(Claims claims, String name) {
        try {
            String value = claims.get(name, String.class);
            if (value == null || value.isBlank()) {
                throw new JwtException("토큰 클레임이 누락되었습니다. claim=" + name);
            }
            return value;
        } catch (JwtException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JwtException("토큰 클레임이 유효하지 않습니다. claim=" + name, e);
        }
    }
}
