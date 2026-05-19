package com.chunbaetour.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link AccessTokenBlacklist} 통합 테스트.
 *
 * <p>Redis 실제 동작(set with TTL, hasKey, active expiration)이 검증 대상이라 Testcontainers Redis를 사용.
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>add → contains true</li>
 *   <li>TTL 만료 후 contains false (Redis active expiration 동작)</li>
 *   <li>음수/0 TTL → 등록 스킵 (방어 코드)</li>
 * </ul>
 */
@SpringBootTest
class AccessTokenBlacklistIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccessTokenBlacklist blacklist;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 다른 테스트와 키 충돌을 막기 위해 blacklist 프리픽스만 정확히 정리.
     */
    @AfterEach
    void cleanup() {
        var keys = redis.keys("auth:blacklist:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void add_then_contains_returns_true() {
        String tokenId = UUID.randomUUID().toString();

        blacklist.add(tokenId, Duration.ofMinutes(5));

        assertThat(blacklist.contains(tokenId)).isTrue();
    }

    @Test
    void contains_with_unregistered_tokenId_returns_false() {
        // 등록 안 한 tokenId는 false. 일반적인 (블랙리스트 적용 안 된) 요청 흐름.
        assertThat(blacklist.contains(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void contains_after_ttl_expiration_returns_false() {
        String tokenId = UUID.randomUUID().toString();
        blacklist.add(tokenId, Duration.ofSeconds(1));

        // Redis active expiration은 부하에 따라 다소 지연될 수 있어 polling으로 안전하게 검증.
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(blacklist.contains(tokenId)).isFalse());
    }

    @Test
    void add_with_zero_ttl_skips_registration() {
        // 이미 만료된 토큰을 블랙리스트에 넣어봤자 의미 없음 + Redis 자체가 0 TTL 거부 → 등록 스킵 분기 검증.
        String tokenId = UUID.randomUUID().toString();
        blacklist.add(tokenId, Duration.ZERO);

        assertThat(blacklist.contains(tokenId)).isFalse();
    }

    @Test
    void add_with_negative_ttl_skips_registration() {
        // 시계 오차나 비정상 입력으로 음수 TTL이 올 가능성 방어.
        String tokenId = UUID.randomUUID().toString();
        blacklist.add(tokenId, Duration.ofSeconds(-10));

        assertThat(blacklist.contains(tokenId)).isFalse();
    }

    @Test
    void add_with_null_ttl_skips_registration() {
        // 외부 호출자가 잘못 null을 넘겨도 NPE 없이 안전하게 무시.
        String tokenId = UUID.randomUUID().toString();
        blacklist.add(tokenId, null);

        assertThat(blacklist.contains(tokenId)).isFalse();
    }
}
