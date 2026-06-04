package com.chunbaetour.domain.auth.config;

import com.chunbaetour.domain.auth.security.CorsProperties;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.security.RestAccessDeniedHandler;
import com.chunbaetour.domain.auth.security.RestAuthenticationEntryPoint;
import com.chunbaetour.domain.common.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
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
 *   <li>{@code /actuator/health}, {@code /actuator/info} — permitAll (LB health check).
 *       {@code /actuator/prometheus}는 IP allowlist (loopback only) + 운영에서는 별도 management port(9090) + loopback 바인딩으로 이중 보호 (#149).
 *       그 외 {@code /actuator/**}는 denyAll로 차단 (env/beans/mappings 등 정보 노출 방지).</li>
 *   <li>{@code /api/v1/users/**} — USER 권한 필요</li>
 *   <li>{@code /api/v1/merchants/**} — MERCHANT 권한 필요</li>
 *   <li>{@code /api/v1/admin/**} — ADMIN 권한 필요</li>
 *   <li>{@code POST/PATCH/DELETE /api/v1/community/**} — USER·ADMIN 권한 필요 (ADMIN은 중재 역할)</li>
 *   <li>{@code GET /api/v1/community/**} — 비인증 허용</li>
 *   <li>{@code /api/v1/payments/**} — USER 전용 (webhook POST는 permitAll 선 매칭)</li>
 *   <li>{@code /api/v1/yeopjeon/**} — USER·MERCHANT 공용 (상인도 소비자로 엽전 사용 가능)</li>
 *   <li>{@code /api/v1/chat/**} — USER 전용 (PRD: 채팅은 일반 사용자만 이용 가능, MERCHANT/ADMIN 접근 불가)</li>
 *   <li>{@code /api/v1/notifications/**} — USER·MERCHANT 공용 (MERCHANT도 고객센터 알림 수신 대상)</li>
 *   <li>{@code /api/v1/reports/**} — USER 전용 (신고 생성·내 신고 조회; admin 신고 API는 /admin/** 커버)</li>
 *   <li>{@code /api/v1/faqs/**} — USER 전용 (버튼형 FAQ 조회; admin 관리 API는 /admin/faqs/** 커버)</li>
 *   <li>{@code /api/v1/support/**} — USER·MERCHANT 공용 (고객센터 상담; admin 상담 API는 /admin/** 커버)</li>
 *   <li>그 외 — 인증 필요</li>
 * </ul>
 *
 * <p>CSRF disable 근거: 일반 API는 STATELESS + Bearer(Authorization 헤더) 인증이라 브라우저가 cross-site 요청에
 * 자격증명을 자동 첨부하지 않으므로 CSRF 표면이 없다. 자동 전송되는 쿠키에 의존하는 흐름은 refresh Cookie 기반
 * {@code POST /api/v1/auth/reissue} 하나뿐이며, 그 방어는 쿠키 {@code SameSite} 속성으로 처리한다(현재 기본 Lax →
 * cross-site 자동 POST에 쿠키 미첨부). ⚠️ CORS allowedOrigins는 "응답 읽기"만 차단할 뿐 요청 전송 자체는 막지
 * 못하므로 CSRF 방어 수단이 아니다. {@code SameSite=None}으로 전환(cross-site 배포)할 경우 reissue에 별도
 * anti-CSRF(커스텀 헤더 강제 또는 Origin/Referer 검증)를 선행 도입해야 한다. (의사결정: docs/operations/samesite-policy.md)
 *
 * <p>Stateless session: JWT 기반이라 서버 세션 미사용.
 */
@Configuration
@EnableMethodSecurity
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
                // 보안 응답 헤더 표준 (프론트엔드 same-site 연동 하드닝).
                // 목적: 클릭재킹/MIME 스니핑/HTTP 다운그레이드/Referer 누출을 응답 헤더로 방어한다.
                // same-site(web.x / api.x) 배치 전제 — SPA(fetch/XHR)·STOMP(WebSocket) 연동을 깨지 않는 보수적 셋만 적용한다.
                // (full Content-Security-Policy default-src 잠금은 Swagger UI/SockJS iframe 호환 검증이 필요해 후속 분리.)
                .headers(headers -> headers
                        // HSTS: 브라우저가 이후 요청을 무조건 HTTPS로 강제(SSL-strip 중간자 차단). maxAge 1년.
                        //   - 주의: Spring Security는 "요청이 secure로 판정될 때만" HSTS를 내보낸다. 로컬(HTTP)은 미전송이 정상이며,
                        //     운영은 LB가 TLS 종단 후 X-Forwarded-Proto=https를 전달(server.forward-headers-strategy=NATIVE, application.yml:46)해야
                        //     secure로 인식돼 HSTS가 실제 전송된다(dead config 아님 — forward-headers 설정 확인됨).
                        //   - includeSubDomains=false(보수적 1차): 운영 도메인이 apex(example.com)에서 응답되면 true가 모든 하위 도메인에
                        //     HTTPS를 강제해, 아직 HTTP로만 뜬 하위 서비스가 있으면 접근이 막힌다(hyeonmin02/lim-haeun 리뷰). 운영 도메인 구조
                        //     (api 서브도메인 단독 vs apex) 확정 후 true 승격 권장.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(false)
                                .maxAgeInSeconds(31_536_000))
                        // X-Frame-Options: DENY — 어떤 페이지도 본 응답을 iframe으로 임베드 불가(클릭재킹 차단).
                        //   ⚠️ Spring Security 6 기본값이 이미 DENY라 본 명시는 "회귀 방지 고정"일 뿐 행동 변화가 없다(현 develop도 DENY 전송 중).
                        //   SockJS iframe 폴백 전송은 의도적으로 미지원한다 — 표준 WebSocket 전송 사용을 전제로 한다. WebSocket이 차단되는
                        //   구형 브라우저/네트워크에서 SockJS가 iframe 폴백을 타야 하면 STOMP 연결이 실패할 수 있으므로 지원 브라우저 범위
                        //   확정 시 재검토(현재는 기본값과 동일이라 본 PR이 새로 깨는 것은 없음).
                        .frameOptions(frame -> frame.deny())
                        // X-Content-Type-Options: nosniff — 브라우저의 MIME 타입 추측 차단(JSON을 스크립트로 오해석하는 공격 방지).
                        //   Spring Security 기본값과 동일하나 명시 고정.
                        .contentTypeOptions(Customizer.withDefaults())
                        // Referrer-Policy: 교차 출처로 나갈 때 Referer를 origin까지만 노출(경로/쿼리 누출 방지).
                        //   토큰은 Authorization 헤더로 전달돼 URL 노출 경로가 구조적으로 낮지만 방어적으로 적용한다.
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // CSP(최소): frame-ancestors 'none' — X-Frame-Options를 무시하는 최신 브라우저까지 커버하는 클릭재킹 2차 방어.
                        //   리소스 출처(default-src/script-src 등)는 잠그지 않아 Swagger UI/SockJS 정적 리소스 로딩에 영향이 없다.
                        //   full default-src 잠금은 Swagger 호환(프로파일 분기) 검증 후 후속 적용.
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI / OpenAPI 스펙 — 개발 환경 문서 접근
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 인증 전에도 호출 가능해야 하는 endpoint (회원가입/로그인)
                        .requestMatchers("/api/v1/users/auth/**").permitAll()
                        .requestMatchers("/api/v1/merchants/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/auth/**").permitAll()
                        // S4: logout만 인증 필요. permitAll(/api/v1/auth/**)보다 먼저 매칭되어야 우선순위가 적용됨.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Actuator endpoint 권한 — KAN-104 + #149.
                        // exposure include = health, info, prometheus (application.yml). 그 외 endpoint는 yml exposure로도 차단되지만,
                        // 방어적으로 SecurityConfig에서도 명시 거부해 향후 exposure가 잘못 확장돼도 노출 방지 (이중 안전).
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // /actuator/prometheus 이중 방어 (#149):
                        // - 운영(application-prod.yml): management.server.port=9090 + address=127.0.0.1 → main 포트 도달 자체 차단
                        // - 본 SecurityConfig: 그래도 main 포트로 도달한 경우(misconfig)에도 loopback IP만 허용 → 차단 응답:
                        //   * 익명 사용자(일반 시나리오): 401 AUTH_006 (RestAuthenticationEntryPoint가 인증 요구로 해석)
                        //   * 인증된 사용자(role mismatch): 403 (RestAccessDeniedHandler)
                        // 로컬/테스트(management port 분리 안 됨)에서는 localhost 호출이라 통과. ::1은 IPv6 loopback.
                        //
                        // ⚠️ LB/Nginx 뒤 배포 시 주의: request.getRemoteAddr()이 프록시 IP를 반환하면 hasIpAddress 매칭이 깨질 수 있다.
                        // 운영은 management port 분리(옵션 A)가 1차 방어선이라 영향 작지만, X-Forwarded-For 처리(ForwardedHeaderFilter
                        // 또는 server.forward-headers-strategy)가 활성화된 환경에서는 trusted-proxy CIDR 확인 필수.
                        .requestMatchers("/actuator/prometheus")
                        .access(new WebExpressionAuthorizationManager(
                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1')"))
                        .requestMatchers("/actuator/**").denyAll()
                        // WebSocket 핸드셰이크 + SockJS 경로 — STOMP 레벨에서 JWT 인증 처리
                        .requestMatchers("/ws-stomp/**").permitAll()
                        // 신고 접수·내 신고 조회: USER 전용 (ADMIN은 신고 처리자이지 신고자가 아님)
                        .requestMatchers("/api/v1/reports/**").hasRole("USER")
                        // 커뮤니티 쓰기(POST·PATCH·DELETE): USER·ADMIN 모두 허용 (ADMIN은 신고 처리 등 중재 역할)
                        .requestMatchers(HttpMethod.POST, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/community/**").hasAnyRole("USER", "ADMIN")
                        // 커뮤니티 GET 비인증 허용: 목록·단건·댓글 목록 포함 (/companions/**, /free/** 하위 전체)
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/companions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/free/**").permitAll()
                        // PortOne 웹훅: 서버→서버 호출이라 JWT 없음 — permitAll 필수
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // QR 결제 승인/거절은 MERCHANT 전용 — payments/** USER 룰보다 반드시 먼저 선언
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/payments/qr/*/confirm").hasRole("MERCHANT")
                        // 결제/환불은 USER 전용 — webhook permitAll 라인보다 뒤에 위치해야 순서 안전
                        .requestMatchers("/api/v1/payments/**").hasRole("USER")
                        // 동행 점수 조회는 공개 — /users/** USER 룰보다 먼저 선언해야 permitAll 적용
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/companion-score").permitAll()
                        // S5: 페이지별 권한 매핑 — role mismatch 시 RestAccessDeniedHandler가 AUTH_007 응답
                        .requestMatchers("/api/v1/users/**").hasRole("USER")
                        // 상인 신청은 USER·MERCHANT 모두 허용 — 1상인 다중 가게 지원. /merchants/** 보다 먼저 선언해야 우선순위 적용됨
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants/apply").hasAnyRole("USER", "MERCHANT")
                        .requestMatchers("/api/v1/merchants/**").hasRole("MERCHANT")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 검색 관련 보호 API: 최근 검색어 저장/조회는 인증된 USER 전용 (조회 공개 API보다 먼저 선언)
                        .requestMatchers(HttpMethod.POST, "/api/v1/search").hasRole("USER")
                        .requestMatchers("/api/v1/search/recent").hasRole("USER")
                        // 검색 조회 API는 와일드카드 대신 화이트리스트 방식으로 명시하여 권한 누수 방지
                        // 2-1 인기 검색어, 2-2 관광지 검색, 2-3 축제 검색, 2-4 자동완성
                        .requestMatchers(HttpMethod.GET, "/api/v1/search", "/api/v1/search/popular", "/api/v1/search/places", "/api/v1/search/festivals", "/api/v1/search/suggest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/search/festivals").permitAll()
                        // 관광지 찜하기/취소는 USER 인증 필요 — GET permitAll보다 먼저 선언해 의도 명확화
                        .requestMatchers(HttpMethod.POST, "/api/v1/places/*/like").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/places/*/like").hasRole("USER")
                        // 관광지 리뷰 작성은 USER 인증 필요 — GET permitAll보다 먼저 선언
                        .requestMatchers(HttpMethod.POST, "/api/v1/places/*/reviews").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/**").permitAll()
                        // 추천 API (4-1: 인기/위치/카테고리, 4-2: 관광지 기반) 는 비로그인 허용 — KAN-157
                        .requestMatchers(HttpMethod.GET, "/api/v1/recommend/**").permitAll()
                        // 가게 공개 조회 — 비로그인 접근 가능 (STORY-12)
                        .requestMatchers(HttpMethod.GET, "/api/v1/shops/*").permitAll()
                        // 스토어 상품 목록·상세 조회 — 비인증 공개 API (STORY-16)
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/products/**").permitAll()
                        // 축제·캘린더 조회 — 비인증 허용
                        .requestMatchers(HttpMethod.GET, "/api/v1/festivals/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/calendar/**").permitAll()
                        // 엽전은 USER·MERCHANT 공용 — 상인도 소비자로 엽전 사용 가능
                        .requestMatchers("/api/v1/yeopjeon/**").hasAnyRole("USER", "MERCHANT")
                        // 채팅은 USER 전용 — MERCHANT/ADMIN 토큰으로 접근 시 AUTH_007 응답
                        .requestMatchers("/api/v1/chat/**").hasRole("USER")
                        // 알림은 USER·MERCHANT 공용 — MERCHANT도 고객센터 알림 수신 대상
                        .requestMatchers("/api/v1/notifications/**").hasAnyRole("USER", "MERCHANT")
                        // 번역은 USER 전용 — 채팅 메시지 번역 기능
                        .requestMatchers("/api/v1/translations/**").hasRole("USER")
                        // 버튼형 FAQ 조회는 USER 전용 — admin 관리 API는 /admin/faqs/** 커버
                        .requestMatchers("/api/v1/faqs/**").hasRole("USER")
                        // 고객센터 상담은 USER·MERCHANT 공용 — ADMIN 접근 시 AUTH_007 응답
                        .requestMatchers("/api/v1/support/**").hasAnyRole("USER", "MERCHANT")
                        // 동행 리뷰 등록은 USER 전용
                        .requestMatchers(HttpMethod.POST, "/api/v1/companion-reviews").hasRole("USER")
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
