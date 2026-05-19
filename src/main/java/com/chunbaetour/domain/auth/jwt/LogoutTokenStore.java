package com.chunbaetour.domain.auth.jwt;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Logout 시 Access blacklist 등록과 Refresh 삭제를 하나의 Redis 명령으로 처리하는 저장소.
 *
 * <p>두 상태 변경이 분리되면 한쪽만 성공하는 부분 로그아웃 상태가 남을 수 있으므로 Lua script로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class LogoutTokenStore {

    private static final String PRESENCE_MARKER = "1";

    private static final RedisScript<Long> LOGOUT_SCRIPT = new DefaultRedisScript<>(
            """
            local ttlMillis = tonumber(ARGV[2])
            if ttlMillis > 0 then
              redis.call('SET', KEYS[1], ARGV[1], 'PX', ttlMillis)
            end
            redis.call('DEL', KEYS[2])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    public void invalidate(long userId, String accessTokenId, Duration accessTtl) {
        long ttlMillis = accessTtl == null ? 0 : accessTtl.toMillis();
        redis.execute(
                LOGOUT_SCRIPT,
                List.of(AccessTokenBlacklist.key(accessTokenId), RefreshTokenStore.key(userId)),
                PRESENCE_MARKER,
                String.valueOf(ttlMillis)
        );
    }
}
