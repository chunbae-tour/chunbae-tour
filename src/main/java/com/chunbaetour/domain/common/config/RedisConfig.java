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
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort);

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
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer jsonSerializer =
                new GenericJacksonJsonRedisSerializer(objectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
