package com.chunbaetour.domain.auth.jwt;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그아웃된 Access Token의 tokenId(jti)를 Redis에 등록해 즉시 무효화하는 deep module.
 *
 * <p>핵심 설계:
 * <ul>
 *   <li><b>Key 구조</b>: {@code auth:blacklist:{tokenId}}. 값은 placeholder({@code "1"})만 저장 — 키 존재 여부만으로 충분.</li>
 *   <li><b>TTL = remainingTtl</b>: 토큰의 남은 만료 시간만큼 Redis에 보관 → 자연 만료된 토큰은 알아서 사라지므로
 *       블랙리스트가 무한히 누적되지 않는다.</li>
 *   <li><b>tokenId가 UUID</b>: 같은 사용자가 다시 로그인해도 새 Access는 새 UUID라 블랙리스트와 충돌 없음.</li>
 * </ul>
 *
 * <p>JWT 인증 필터가 매 요청마다 {@link #contains}를 호출하므로 Redis 라운드트립이 추가된다. 단일 GET 비용은
 * 무시할 수 있는 수준이지만 P99 지연이 문제가 되면 로컬 캐시(Caffeine 등)로 감쌀 수 있다 (S4 범위 외).
 */
@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

    private static final String KEY_PREFIX = "auth:blacklist:";
    /** 값 자체는 무의미하므로 메모리 절약 차원에서 가장 짧은 문자열을 쓴다. */
    private static final String PRESENCE_MARKER = "1";

    private final StringRedisTemplate redis;

    /**
     * tokenId를 블랙리스트에 등록한다.
     *
     * <p>{@code ttl}이 0 이하면 등록을 스킵한다 — 이미 만료된 토큰을 굳이 등록할 필요가 없으며,
     * Redis는 0 이하 TTL을 거부하므로 방어 코드 차원에서 명시적으로 분기한다.
     *
     * @param tokenId Access Token의 {@code tid} 클레임 (UUID)
     * @param ttl     남은 만료 시간 ({@code AccessClaims.expiresAt - now}). 이 시간이 지나면 자동 삭제.
     */
    public void add(String tokenId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.opsForValue().set(key(tokenId), PRESENCE_MARKER, ttl);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인한다.
     *
     * <p>JWT 인증 필터가 매 요청에서 호출. 값에 의미가 없으므로 {@code hasKey}로 충분.
     */
    public boolean contains(String tokenId) {
        Boolean hasKey = redis.hasKey(key(tokenId));
        return Boolean.TRUE.equals(hasKey);
    }

    private String key(String tokenId) {
        return KEY_PREFIX + tokenId;
    }
}
