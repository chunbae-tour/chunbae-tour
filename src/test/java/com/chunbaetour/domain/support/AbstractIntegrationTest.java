package com.chunbaetour.domain.support;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 모든 인증/인가 통합 테스트의 공통 베이스.
 *
 * <p>핵심 설계: <b>JVM 단일 컨테이너 재사용 (Singleton Containers)</b>.
 * 정적 블록에서 MySQL/Redis 컨테이너를 한 번만 시작하고, 모든 서브클래스가 동일 컨테이너를 공유한다.
 * 이로써:
 * <ul>
 *   <li>매 테스트 클래스마다 컨테이너 시작/중지를 반복하지 않아 전체 빌드 시간이 크게 줄어든다.</li>
 *   <li>테스트 간 데이터 누수를 막기 위해 각 테스트 클래스는 {@code @AfterEach}로 본인 도메인 데이터를 정리해야 한다.</li>
 *   <li>Redis 키는 모든 테스트가 공유하므로 키 충돌 방지를 위해 도메인 prefix(예: {@code auth:refresh:})를 유지해야 한다.</li>
 * </ul>
 *
 * <p>JUnit의 {@code @Testcontainers}/{@code @Container} 라이프사이클을 쓰지 않는 이유:
 * 그 어노테이션은 컨테이너를 테스트 클래스 단위로 시작/중지한다. 우리는 JVM 단위 공유가 목적이므로
 * 정적 초기화 + JVM 종료 시 Testcontainers의 Ryuk(컨테이너 자동 cleanup 데몬)에 위임한다.
 *
 * <p>{@link DynamicPropertySource}로 Spring 컨텍스트가 시작되기 전에 동적 호스트/포트를 주입한다.
 */
public abstract class AbstractIntegrationTest {

    @Autowired(required = false)
    private StringRedisTemplate redisForBaseCleanup;

    /**
     * 모든 통합 테스트 끝에 Rate Limit Redis 키({@code ratelimit:*})를 정리한다.
     *
     * <p>같은 JVM에서 127.0.0.1로 반복 호출하면 카운터가 누적되어 다른 테스트가 자기 한도에 부딪힐 수 있다.
     * 본 base @AfterEach가 prefix 단위로 비워 클래스 간 격리를 보장한다 (auth:refresh:* / auth:blacklist:* /
     * accountRepository.deleteAll() 같은 도메인별 cleanup은 각 테스트가 자기 @AfterEach에서 책임).
     *
     * <p>JUnit 5의 @AfterEach는 subclass → superclass 순으로 실행되므로 본 메서드는 항상 마지막에 동작 — 다른
     * cleanup이 끝난 후 ratelimit 키를 마지막에 비운다.
     */
    @AfterEach
    void cleanupRateLimitKeys() {
        if (redisForBaseCleanup == null) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().match("ratelimit:*").count(100).build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redisForBaseCleanup.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisForBaseCleanup.delete(keys);
        }
    }


    /** MySQL 8.4 컨테이너. 운영 DB 버전과 일치시켜 SQL 호환성 보장. */
    private static final MySQLContainer<?> MYSQL;

    /** Redis 7-alpine 컨테이너. Refresh Token Rotation 저장소로 사용. */
    private static final GenericContainer<?> REDIS;

    static {
        MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));
        MYSQL.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS.start();
    }

    /**
     * Spring 컨텍스트가 빈을 만들기 전에 {@code spring.datasource.*}, {@code spring.data.redis.*}를
     * 동적으로 주입한다.
     *
     * <p>{@code DynamicPropertyRegistry.add}는 {@code Supplier}를 받으므로, 컨테이너가 시작된 후
     * 실제 매핑된 포트를 lazy하게 조회한다. (정적 초기화 순서 문제 회피)
     */
    @DynamicPropertySource
    static void overrideContainerProperties(DynamicPropertyRegistry registry) {
        // MySQL: Testcontainers가 자동 할당한 호스트/포트로 JDBC URL 구성
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);

        // Redis: GenericContainer는 ServiceConnection 자동 지원이 없어 수동 주입
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 테스트용 Redis는 비밀번호 없음 (운영과 다름)
        registry.add("spring.data.redis.password", () -> "");

        // 테스트 격리: 스키마는 매 컨텍스트 시작 시 새로 생성. 데이터 누적 방지는 각 테스트의 @AfterEach 책임
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // JWT: 테스트 전용 더미 시크릿 (32 바이트 이상 검증 통과 필요)
        registry.add("jwt.secret", () -> "test-only-secret-32-bytes-min-xxxxxx");
        registry.add("jwt.access-token-ttl", () -> "PT30M");
        registry.add("jwt.refresh-token-ttl", () -> "P7D");

        // Cookie: 테스트는 HTTP라 secure=false 고정. SameSite는 운영과 동일
        registry.add("cookie.refresh-token.name", () -> "refreshToken");
        registry.add("cookie.refresh-token.secure", () -> "false");
        registry.add("cookie.refresh-token.same-site", () -> "Lax");
        registry.add("cookie.refresh-token.path", () -> "/api/v1/auth");

        // CORS: 테스트는 로컬 origin만 허용
        registry.add("cors.allowed-origins", () -> "http://localhost:3000,http://localhost:5173");
        registry.add("cors.allowed-methods", () -> "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        registry.add("cors.allowed-headers", () -> "Authorization,Content-Type,Accept");
        registry.add("cors.allow-credentials", () -> "true");
        registry.add("cors.max-age", () -> "PT1H");

        // Rate Limit: 기본은 yml(true) 사용. 같은 IP(127.0.0.1)로 반복 호출 시 카운터 누적 방지를 위해
        // 본 base의 @AfterEach가 ratelimit:* 키를 정리한다 (위 cleanupRateLimitKeys 메서드).
        // RateLimitIntegrationTest는 별도 @DynamicPropertySource로 짧은 정책 주입.

        // 외부 API/PG 더미 값 — 다른 도메인 yml이 환경변수 필수로 두어 부팅 시점에
        // PlaceholderResolutionException이 발생하지 않도록. 통합 테스트는 외부 호출 안 함.
        registry.add("kakao.map.api-key", () -> "test-only-kakao-key");
        registry.add("portone.secret", () -> "test-only-portone-secret");
        // PortOneProperties.webhookSecret이 @NotBlank로 검증 (e63a79e KAN-70에서 도입) — 빈 값으로 두면
        // BindValidationException으로 통합 테스트 컨텍스트 로드가 실패한다. 운영에서는 환경변수로 주입.
        registry.add("portone.webhook-secret", () -> "test-only-portone-webhook-secret");
        registry.add("portone.store-id", () -> "test-only-portone-store-id");
        registry.add("portone.channel.card", () -> "test-channel-card");
        registry.add("portone.channel.kakao-pay", () -> "test-channel-kakao-pay");
        registry.add("portone.channel.toss-pay", () -> "test-channel-toss-pay");
        registry.add("portone.channel.foreign-card", () -> "test-channel-foreign-card");
        registry.add("portone.webhook-secret", () -> "test-only-webhook-secret");
    }
}
