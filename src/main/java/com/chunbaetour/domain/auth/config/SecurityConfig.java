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
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus} — permitAll (LB health check + Prometheus scrape).
 *       그 외 {@code /actuator/**}는 denyAll로 차단 (env/beans/mappings 등 정보 노출 방지).
 *       {@code /actuator/prometheus}는 운영 배포 전 IP allowlist 추가 필수 (release blocker #149 — Trusted Proxy / 별도 management port 검토)</li>
 *   <li>{@code /api/v1/users/**} — USER 권한 필요</li>
 *   <li>{@code /api/v1/merchants/**} — MERCHANT 권한 필요</li>
 *   <li>{@code /api/v1/admin/**} — ADMIN 권한 필요</li>
 *   <li>{@code POST/PATCH/DELETE /api/v1/community/**} — USER·ADMIN 권한 필요 (ADMIN은 중재 역할)</li>
 *   <li>{@code GET /api/v1/community/**} — 비인증 허용</li>
 *   <li>{@code /api/v1/payments/**} — USER 전용 (webhook POST는 permitAll 선 매칭)</li>
 *   <li>{@code /api/v1/yeopjeon/**} — USER·MERCHANT 공용 (상인도 소비자로 엽전 사용 가능)</li>
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
                        // Actuator endpoint 권한 — KAN-104.
                        // exposure include = health, info, prometheus (application.yml). 그 외 endpoint는 yml exposure로도 차단되지만,
                        // 방어적으로 SecurityConfig에서도 명시 거부해 향후 exposure가 잘못 확장돼도 노출 방지 (이중 안전).
                        // prometheus는 본 PR에서 permitAll. 운영 배포 전 IP allowlist 또는 별도 management port 분리 필수.
                        // TODO(#149): /actuator/prometheus 운영 보호 — IP allowlist 또는 management.server.port 분리. release blocker.
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // 커뮤니티 쓰기(POST·PATCH·DELETE): USER·ADMIN 모두 허용 (ADMIN은 신고 처리 등 중재 역할)
                        .requestMatchers(HttpMethod.POST, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        // 커뮤니티 GET 비인증 허용: 목록·단건·댓글 목록 포함 (/companions/**, /free/** 하위 전체)
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/companions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/free/**").permitAll()
                        // PortOne 웹훅: 서버→서버 호출이라 JWT 없음 — permitAll 필수
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // 결제/환불은 USER 전용 — webhook permitAll 라인보다 뒤에 위치해야 순서 안전
                        .requestMatchers("/api/v1/payments/**").hasRole("USER")
                        // S5: 페이지별 권한 매핑 — role mismatch 시 RestAccessDeniedHandler가 AUTH_007 응답
                        .requestMatchers("/api/v1/users/**").hasRole("USER")
                        // 상인 신청은 USER 권한 — /merchants/** 보다 먼저 선언해야 우선순위 적용됨
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants/apply").hasRole("USER")
                        .requestMatchers("/api/v1/merchants/**").hasRole("MERCHANT")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 검색 조회 API는 와일드카드 대신 화이트리스트 방식으로 명시하여 권한 누수 방지
                        // 2-1 인기 검색어, 2-2 관광지 검색, 2-3 축제 검색, 2-4 자동완성
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/popular", "/api/v1/search/places", "/api/v1/search/festivals", "/api/v1/search/suggest").permitAll()
                        // 엽전은 USER·MERCHANT 공용 — 상인도 소비자로 엽전 사용 가능
                        .requestMatchers("/api/v1/yeopjeon/**").hasAnyRole("USER", "MERCHANT")
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
