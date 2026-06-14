package com.chunbaetour.domain.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // 전송 중 암호화(TLS) 토글. 운영 ElastiCache(Valkey)는 in-transit encryption ON이라 rediss:// 필요(ECS E2/E4).
    // 로컬/CI(평문 단일 Redis)는 기본 false → 기존 redis:// 그대로. Lettuce 쪽은 spring.data.redis.ssl.enabled가
    // Spring Boot 자동구성으로 동일하게 반영되지만, Redisson은 수동 구성이라 같은 프로퍼티를 별도로 읽어 스킴을 맞춘다.
    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // ElastiCache는 단일 샤드(cluster-mode-disabled, 슬롯 0-16383 단일)라 useSingleServer로 구성 엔드포인트에 직접 연결.
        //   샤드 분리(cluster-mode-enabled)는 ECS 안정화 후 별도 진행 — 그때 useClusterServers + 키 hash tag 전환 필요.
        // 비밀번호: ElastiCache AUTH 미설정(TLS만) → redisPassword가 빈값이면 setPassword 스킵(기존 동작 유지).
        String scheme = redisSslEnabled ? "rediss://" : "redis://";
        var singleServer = config.useSingleServer()
                .setAddress(scheme + redisHost + ":" + redisPort);

        if (redisPassword != null && !redisPassword.isBlank()) {
            singleServer.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }

    /**
     * 문자열 기반 Redis 연산용 템플릿.
     * 용도: ZSet(인기 검색어 랭킹), GEO(위치 기반 탐색), LIST(최근 검색어), INCR(조회수/찜수)
     * Spring Boot 자동 구성의 StringRedisTemplate과 동일하나, 명시적으로 Bean 선언해 관리.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 객체 직렬화(JSON) 기반 Redis 연산용 템플릿.
     * 용도: 관광지 상세 캐싱(place:{placeId}), 위치 기반 결과 캐싱 등
     * - key  : StringRedisSerializer
     * - value: GenericJacksonJsonRedisSerializer (Jackson 3 기반, Spring Data Redis 4.0 권장)
     *          JavaTimeModule 등록 불필요 — Jackson 3에서 Java 8 날짜/시간 타입 기본 내장
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // 타입정보(@class) 보존 직렬화기 — 캐시/템플릿 GET 시 DTO가 LinkedHashMap으로 복원되는 것 방지 (KAN-264).
        GenericJacksonJsonRedisSerializer jsonSerializer =
                RedisJsonSerializerFactory.typePreservingSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
