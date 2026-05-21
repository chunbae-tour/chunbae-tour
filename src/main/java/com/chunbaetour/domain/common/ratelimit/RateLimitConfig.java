package com.chunbaetour.domain.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rate Limit 영역의 {@link RateLimitProperties} 바인딩 활성화.
 *
 * <p>record 기반 {@code @ConfigurationProperties}는 명시적 등록이 필요하다 (Spring Boot 자동 스캔 미적용).
 * 본 클래스가 단일 진입점.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
