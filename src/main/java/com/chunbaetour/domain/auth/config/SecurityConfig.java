package com.chunbaetour.domain.auth.config;

import com.chunbaetour.domain.auth.security.CorsProperties;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.security.RestAccessDeniedHandler;
import com.chunbaetour.domain.auth.security.RestAuthenticationEntryPoint;
import com.chunbaetour.domain.common.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 필터 체인 구성.
 *
 * <p>S5 변경 사항:
 * <ul>
 *   <li>상인/관리자 페이지의 인증/보호 endpoint 매핑을 추가 — 페이지별 endpoint 분리 정책 (Auth foundation PRD §Further Notes 참조)</li>
 * </ul>
 *
 * <p>URL 권한 모델 (S5 기준):
 * <ul>
 *   <li>{@code /api/v1/users/auth/**} — USER 회원가입/로그인 (permitAll)</li>
 *   <li>{@code /api/v1/merchants/auth/**} — MERCHANT 로그인 (permitAll)</li>
 *   <li>{@code /api/v1/admin/auth/**} — ADMIN 로그인 (permitAll)</li>
 *   <li>{@code POST /api/v1/auth/logout} — 인증 필요 (S4)</li>
 *   <li>{@code /api/v1/auth/**} — 공통 토큰 API (reissue 등, permitAll)</li>
 *   <li>{@code /actuator/**} — 헬스체크/info (permitAll)</li>
 *   <li>{@code /api/v1/users/**} — USER 권한 필요</li>
 *   <li>{@code /api/v1/merchants/**} — MERCHANT 권한 필요</li>
 *   <li>{@code /api/v1/admin/**} — ADMIN 권한 필요</li>
 *   <li>{@code /api/v1/chat/**} — USER 전용 (PRD: 채팅은 일반 사용자만 이용 가능, MERCHANT/ADMIN 접근 불가)</li>
 *   <li>그 외 — 인증 필요</li>
 * </ul>
 *
 * <p>CSRF disable 근거: REST API + JWT/HttpOnly Cookie 조합이라 폼 제출이 없어 CSRF 토큰 흐름 불필요.
 * Cookie 흐름의 CSRF 방어는 SameSite=Lax + Origin 검증(CORS allowedOrigins)으로 처리한다.
 *
 * <p>Stateless session: JWT 기반이라 서버 세션 미사용.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS는 명시적으로 활성화 (아래 corsConfigurationSource bean 사용)
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 인증 전에도 호출 가능해야 하는 endpoint (회원가입/로그인)
                        .requestMatchers("/api/v1/users/auth/**").permitAll()
                        .requestMatchers("/api/v1/merchants/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/auth/**").permitAll()
                        // S4: logout만 인증 필요. permitAll(/api/v1/auth/**)보다 먼저 매칭되어야 우선순위가 적용됨.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // PortOne 웹훅: 서버→서버 호출이라 JWT 없음 — permitAll 필수
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // S5: 페이지별 권한 매핑 — role mismatch 시 RestAccessDeniedHandler가 AUTH_007 응답
                        .requestMatchers("/api/v1/users/**").hasRole("USER")
                        .requestMatchers("/api/v1/merchants/**").hasRole("MERCHANT")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // [CodeRabbit 리뷰 반영] 검색 조회 API는 와일드카드 대신 화이트리스트 방식으로 명시하여 권한 누수 방지
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/popular", "/api/v1/search/places").permitAll()
                        // 채팅은 USER 전용 — MERCHANT/ADMIN 토큰으로 접근 시 AUTH_007 응답
                        .requestMatchers("/api/v1/chat/**").hasRole("USER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // JWT 필터를 UsernamePassword 앞에 등록 → Bearer 토큰을 먼저 검증해 SecurityContext 채움
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate Limit 필터를 JWT 앞에 두어 인증 실패 시도도 카운트 대상에 포함 (무차별 공격 방어 우선)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS 정책 빈.
     *
     * <p>{@link CorsProperties}에서 허용 origin/method/header를 읽어 모든 경로({@code /**})에 적용.
     * 도메인별로 다른 정책이 필요해지면 {@code registerCorsConfiguration("/api/v1/auth/**", ...)} 식으로 분리.
     *
     * <p>주요 동작:
     * <ul>
     *   <li>Preflight(OPTIONS) 요청은 본 빈이 자동 처리 (Spring Security CorsFilter)</li>
     *   <li>{@code allowCredentials=true}이면 응답에 {@code Access-Control-Allow-Credentials: true} + 명시 origin 반환</li>
     *   <li>{@code maxAge}만큼 브라우저가 Preflight 응답을 캐싱</li>
     * </ul>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setAllowCredentials(corsProperties.allowCredentials());
        config.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 전역 적용 — 도메인별 차별화 정책은 후속 PRD에서 분리
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
