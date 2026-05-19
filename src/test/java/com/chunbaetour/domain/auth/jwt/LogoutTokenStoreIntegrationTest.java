package com.chunbaetour.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link LogoutTokenStore} 통합 테스트.
 *
 * <p>Access blacklist 등록과 Refresh 삭제가 하나의 Redis Lua script로 함께 수행되는지 검증한다.
 */
@SpringBootTest
class LogoutTokenStoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LogoutTokenStore logoutTokenStore;

    @Autowired
    private AccessTokenBlacklist accessTokenBlacklist;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        deleteByPrefix("auth:refresh:*");
        deleteByPrefix("auth:blacklist:*");
    }

    @Test
    void invalidate_blacklists_access_and_deletes_refresh_in_one_operation() {
        long userId = 42L;
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();
        refreshTokenStore.save(userId, refreshTokenId, Duration.ofMinutes(5));

        logoutTokenStore.invalidate(userId, accessTokenId, Duration.ofMinutes(5));

        assertThat(accessTokenBlacklist.contains(accessTokenId)).isTrue();
        assertThat(refreshTokenStore.exists(userId, refreshTokenId)).isFalse();
    }

    @Test
    void invalidate_with_expired_access_ttl_still_deletes_refresh() {
        long userId = 43L;
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();
        refreshTokenStore.save(userId, refreshTokenId, Duration.ofMinutes(5));

        logoutTokenStore.invalidate(userId, accessTokenId, Duration.ZERO);

        assertThat(accessTokenBlacklist.contains(accessTokenId)).isFalse();
        assertThat(refreshTokenStore.exists(userId, refreshTokenId)).isFalse();
    }

    private void deleteByPrefix(String pattern) {
        var keys = redis.keys(pattern);
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
