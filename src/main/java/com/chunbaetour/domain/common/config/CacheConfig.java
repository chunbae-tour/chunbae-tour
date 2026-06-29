package com.chunbaetour.domain.common.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Cache — Redis 기반 캐시 설정.
 * 기본 TTL 1시간. 캐시 이름별 TTL 커스터마이징 필요 시 withInitialCacheConfigurations() 추가.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    // GET 실패(역직렬화 오류 등)를 캐시미스로 처리 — 500 전파 방지
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 타입정보(@class) 보존 직렬화기 — 캐시 HIT 시 DTO가 LinkedHashMap으로 복원돼 ClassCastException 나는 것 방지 (KAN-264).
        GenericJacksonJsonRedisSerializer jsonSerializer =
                RedisJsonSerializerFactory.typePreservingSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        RedisCacheConfiguration companionScoreConfig = config
                .entryTtl(Duration.ofMinutes(10));

        // 정적 도메인 번역 결과 — translation_cache(DB) read-through, hit율 높아 TTL 길게
        RedisCacheConfiguration translationConfig = config
                .entryTtl(Duration.ofHours(24));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("companionScore:v2", companionScoreConfig)
                .withCacheConfiguration("translation", translationConfig)
                // 캐시 통계 활성화 — @Cacheable 캐시의 hit/miss를 RedisCache가 집계하고 Micrometer가 cache.* 메트릭으로 노출
                // (모니터링 1단계). 미설정 시 RedisCache 기본 통계 OFF라 hit율이 수집되지 않는다.
                .enableStatistics()
                .build();
    }
}
