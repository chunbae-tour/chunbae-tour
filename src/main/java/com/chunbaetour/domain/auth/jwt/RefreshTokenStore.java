package com.chunbaetour.domain.auth.jwt;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh Token의 "현재 유효한 tokenId"를 Redis에 저장/조회/회전/삭제하는 deep module.
 *
 * <p>핵심 설계:
 * <ul>
 *   <li><b>Key 구조</b>: {@code auth:refresh:{userId}} 단일 키에 현재 유효한 tokenId 하나를 저장한다.
 *       이는 "한 사용자 = 한 세션" 모델 (PRD 명시). 추후 멀티 디바이스 지원이 필요하면 키 구조를
 *       {@code auth:refresh:{userId}:{tokenId}} 또는 Hash로 확장한다.</li>
 *   <li><b>Rotation 원자성</b>: {@link #rotate}는 Lua 스크립트로 GET-비교-SET을 단일 명령으로 처리한다.
 *       Redis는 Lua 실행 중 다른 명령을 받지 않으므로 동시 재발급 요청이 들어와도 한쪽만 성공한다 (CAS 의미).
 *       이를 통해 "탈취된 Refresh Token이 합법 사용자와 동시 재발급 시도하는 경우" 중 하나는 거부된다.</li>
 *   <li><b>TTL 일치</b>: Refresh Token 자체 만료(JWT exp)와 Redis 키 TTL을 같게 두어, JWT만 살아있고
 *       Redis는 만료되거나 그 반대의 미스매치를 피한다. 호출자가 TTL을 인자로 명시한다.</li>
 * </ul>
 *
 * <p>외부에서는 JWT 라이브러리/Redis 클라이언트를 알 필요 없이 4개 메서드만 사용한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    /**
     * Rotation 전용 Lua 스크립트.
     *
     * <p>의도: 키에 저장된 현재 tokenId가 {@code ARGV[1]}(oldTokenId)과 일치할 때만
     * {@code ARGV[2]}(newTokenId)로 덮어쓰고 TTL을 갱신한다. CAS(Compare-And-Swap)와 동일한 의미.
     *
     * <p>반환:
     * <ul>
     *   <li>1: 회전 성공 (current == old, SET 수행됨)</li>
     *   <li>0: 회전 실패 (current != old or 키 없음 — 이미 다른 토큰으로 회전됐거나 삭제됨)</li>
     * </ul>
     *
     * <p>Redis 단일 스레드 모델 덕에 Lua 실행 중에는 다른 명령이 끼어들 수 없어 원자성이 보장된다.
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
              return 1
            else
              return 0
            end
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    /**
     * 새로운 Refresh Token의 tokenId를 사용자 키에 저장한다.
     *
     * <p>이미 키가 있으면 덮어쓴다 (한 사용자 = 한 세션 모델). 즉, 같은 계정으로 다른 디바이스에서
     * 재로그인하면 이전 디바이스의 Refresh는 즉시 무효화된다.
     *
     * @param userId  사용자 ID
     * @param tokenId 새로 발급된 Refresh Token의 tokenId (JWT tid claim과 일치)
     * @param ttl     Redis 키 TTL. JWT refresh-token-ttl과 동일해야 함 (호출자 책임)
     */
    public void save(long userId, String tokenId, Duration ttl) {
        redis.opsForValue().set(key(userId), tokenId, ttl);
    }

    /**
     * 주어진 tokenId가 현재 사용자 키에 저장된 "유효한" tokenId와 일치하는지 확인한다.
     *
     * <p>일치하지 않거나 키가 없으면 false. 보통 reissue/logout에서 "이 토큰이 아직 살아있는가?" 검증에 사용.
     *
     * <p>주의: 단순 조회이므로 다른 스레드가 동시에 회전 중이면 결과가 stale일 수 있다.
     * 진짜 mutual exclusion이 필요하면 {@link #rotate}의 CAS를 사용할 것.
     */
    public boolean exists(long userId, String tokenId) {
        String stored = redis.opsForValue().get(key(userId));
        return tokenId != null && tokenId.equals(stored);
    }

    /**
     * 현재 저장된 tokenId가 {@code oldTokenId}일 때만 {@code newTokenId}로 원자적으로 교체한다.
     *
     * <p>전형적인 Refresh Token Rotation 흐름:
     * <pre>
     *   1. 클라이언트가 oldRefresh를 들고 /reissue 호출
     *   2. 서버가 JWT 검증 → oldTokenId 추출
     *   3. store.rotate(userId, oldTokenId, newTokenId, ttl) 호출 → 성공 시 새 토큰 발급
     *   4. 동시 두 번째 reissue 요청은 current가 이미 newTokenId라 실패 (탈취 시도 차단)
     * </pre>
     *
     * @return true=교체 성공, false=교체 실패(이미 회전됐거나 키 없음 → REFRESH_TOKEN_INVALID로 매핑)
     */
    public boolean rotate(long userId, String oldTokenId, String newTokenId, Duration ttl) {
        Long result = redis.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                oldTokenId,
                newTokenId,
                String.valueOf(ttl.toSeconds())
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 사용자의 Refresh 키를 즉시 삭제한다.
     *
     * <p>로그아웃(S4) 또는 강제 무효화 시 사용. 삭제 후에는 어떤 Refresh Token으로도 reissue 불가
     * (다시 로그인해야 신규 키 생성).
     */
    public void delete(long userId) {
        redis.delete(key(userId));
    }

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
