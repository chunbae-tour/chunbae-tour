package com.chunbaetour.domain.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 보안 응답 헤더 회귀 가드 ({@link SecurityConfig#securityFilterChain}).
 *
 * <p>런타임 헤더가 핵심인 PR이라 compileJava만으로는 "헤더가 실제로 응답에 실리는지"를 보장하지 못한다.
 * 본 테스트는 SecurityFilterChain을 통과한 응답에 의도한 헤더가 정확한 값으로 실리는지 고정한다(리뷰 반영).
 *
 * <p>대상 요청: {@code GET /actuator/health}(permitAll). 보안 헤더는 endpoint 결과와 무관하게 필터 체인에서
 * 모든 응답에 부착되므로, 인증 없이 200을 받는 경로면 충분하다.
 *
 * <p>HSTS는 단언하지 않는다 — Spring Security는 요청이 secure로 판정될 때만 HSTS를 내보내는데, 테스트는
 * 평문 HTTP(MockMvc)라 secure가 아니어서 HSTS가 정상적으로 미전송된다(운영 HTTPS에서만 전송).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityHeadersIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("보안 응답 헤더(X-Frame-Options/nosniff/Referrer-Policy/CSP)가 모든 응답에 실린다")
    void securityHeaders_arePresentOnResponses() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                // 클릭재킹 — iframe 임베드 전면 차단
                .andExpect(header().string("X-Frame-Options", "DENY"))
                // MIME 스니핑 차단
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                // Referer 누출 최소화
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                // 클릭재킹 2차 방어(리소스 출처 미잠금)
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'"));
    }
}
