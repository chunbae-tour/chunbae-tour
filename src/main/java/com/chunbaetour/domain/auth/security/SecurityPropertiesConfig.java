package com.chunbaetour.domain.auth.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Auth security 영역의 {@link org.springframework.boot.context.properties.ConfigurationProperties}
 * 바인딩을 한 곳에서 활성화한다.
 *
 * <p>여기에 등록하지 않으면 Spring Boot가 record 기반 @ConfigurationProperties를 빈으로 만들지 않는다.
 * <ul>
 *   <li>{@link CookieProperties} — Refresh Cookie 정책</li>
 *   <li>{@link CorsProperties} — CORS 허용 origin/method/header</li>
 * </ul>
 *
 * <p>JWT 관련 {@code JwtProperties}는 별도 {@code com.chunbaetour.domain.auth.jwt.JwtConfig}에서 관리한다
 * (도메인 응집도 유지).
 */
@Configuration
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class})
public class SecurityPropertiesConfig {
}
