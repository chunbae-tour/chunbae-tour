package com.chunbaetour.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link RefreshTokenStore} 통합 테스트.
 *
 * <p>Redis 실서버 동작이 핵심이므로 Testcontainers Redis를 사용한다.
 * 단위 테스트로 mocking할 경우 Lua 스크립트의 원자성을 검증할 수 없으므로 통합 테스트로 분류.
 *
 * <p>검증 항목 (PRD AC):
 * <ul>
 *   <li>save → exists true</li>
 *   <li>delete 후 exists false</li>
 *   <li>rotate 후 old false + new true</li>
 *   <li>TTL 만료 후 exists false (짧은 TTL로 검증)</li>
 *   <li>동시 rotate 시 한쪽만 성공 (CAS 원자성)</li>
 * </ul>
 */
@SpringBootTest
class RefreshTokenStoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenStore store;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 각 테스트가 독립적으로 시작하도록 Redis 키를 모두 지운다.
     *
     * <p>JVM 단위 컨테이너 공유 모델이라 다른 테스트의 키가 남으면 결과가 오염된다.
     * 도메인 prefix({@code auth:refresh:*})만 지워도 충분하지만, 안전을 위해 본 테스트가 만지는 키만
     * 정확히 지운다.
     */
    @AfterEach
    void cleanup() {
        var keys = redis.keys("auth:refresh:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void save_then_exists_returns_true() {
        long userId = 1L;
        String tokenId = UUID.randomUUID().toString();

        store.save(userId, tokenId, Duration.ofMinutes(5));

        assertThat(store.exists(userId, tokenId)).isTrue();
    }

    @Test
    void exists_with_different_tokenId_returns_false() {
        long userId = 2L;
        store.save(userId, "stored-id", Duration.ofMinutes(5));

        assertThat(store.exists(userId, "other-id")).isFalse();
    }

    @Test
    void exists_with_no_key_returns_false() {
        // save를 호출하지 않은 사용자 ID. 키가 아예 존재하지 않는 상황.
        assertThat(store.exists(99L, "any-id")).isFalse();
    }

    @Test
    void delete_then_exists_returns_false() {
        long userId = 3L;
        String tokenId = UUID.randomUUID().toString();
        store.save(userId, tokenId, Duration.ofMinutes(5));

        store.delete(userId);

        assertThat(store.exists(userId, tokenId)).isFalse();
    }

    @Test
    void rotate_replaces_old_with_new_atomically() {
        long userId = 4L;
        String oldId = UUID.randomUUID().toString();
        String newId = UUID.randomUUID().toString();
        store.save(userId, oldId, Duration.ofMinutes(5));

        boolean rotated = store.rotate(userId, oldId, newId, Duration.ofMinutes(5));

        assertThat(rotated).isTrue();
        // 이전 ID는 더이상 유효하지 않다 (탈취 토큰 차단의 핵심)
        assertThat(store.exists(userId, oldId)).isFalse();
        // 새 ID가 살아 있다
        assertThat(store.exists(userId, newId)).isTrue();
    }

    @Test
    void rotate_with_mismatched_old_returns_false_and_keeps_current() {
        long userId = 5L;
        String currentId = UUID.randomUUID().toString();
        store.save(userId, currentId, Duration.ofMinutes(5));

        boolean rotated = store.rotate(userId, "stale-id", "new-id", Duration.ofMinutes(5));

        assertThat(rotated).isFalse();
        // 잘못된 old를 들고 와도 현재 값이 유지된다 (이미 회전된 토큰의 재시도 거부)
        assertThat(store.exists(userId, currentId)).isTrue();
    }

    @Test
    void rotate_when_key_missing_returns_false() {
        boolean rotated = store.rotate(6L, "any-old", "any-new", Duration.ofMinutes(5));

        assertThat(rotated).isFalse();
    }

    @Test
    void exists_after_ttl_expiration_returns_false() {
        long userId = 7L;
        String tokenId = UUID.randomUUID().toString();
        // TTL 1초 — 빠른 검증
        store.save(userId, tokenId, Duration.ofSeconds(1));

        // Redis active expiration은 환경 부하에 따라 늦어질 수 있어 짧게 polling한다.
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(store.exists(userId, tokenId)).isFalse());
    }

    /**
     * 동시성 검증: 두 스레드가 같은 oldId로 회전 시도 → 한쪽만 성공.
     *
     * <p>이는 "탈취된 Refresh Token + 합법 클라이언트의 동시 reissue" 시나리오를 모사한다.
     * Lua 스크립트의 CAS 의미가 깨지면 둘 다 성공할 수 있어 매우 위험하다.
     */
    @Test
    void concurrent_rotate_only_one_succeeds() throws Exception {
        long userId = 8L;
        String oldId = UUID.randomUUID().toString();
        String newIdA = "new-A";
        String newIdB = "new-B";
        store.save(userId, oldId, Duration.ofMinutes(5));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(() -> store.rotate(userId, oldId, newIdA, Duration.ofMinutes(5)));
            Future<Boolean> b = pool.submit(() -> store.rotate(userId, oldId, newIdB, Duration.ofMinutes(5)));

            boolean resultA = a.get(5, TimeUnit.SECONDS);
            boolean resultB = b.get(5, TimeUnit.SECONDS);

            // 정확히 하나만 true (XOR). 두 스레드 모두 성공하면 race condition 발생 → 실패해야 함
            assertThat(resultA ^ resultB).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
