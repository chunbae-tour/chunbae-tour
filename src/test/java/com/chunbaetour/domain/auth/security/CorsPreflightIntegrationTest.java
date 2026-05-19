package com.chunbaetour.domain.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS Preflight(OPTIONS) 동작 검증.
 *
 * <p>브라우저가 cross-origin 요청 직전에 보내는 OPTIONS 요청에 대해 Spring Security CorsFilter가
 * 올바른 응답 헤더를 반환하는지 확인한다. Cookie credential 흐름의 사전 조건.
 *
 * <p>주요 검증 헤더:
 * <ul>
 *   <li>{@code Access-Control-Allow-Origin} — 명시적 origin (와일드카드 금지)</li>
 *   <li>{@code Access-Control-Allow-Credentials: true} — Cookie 전송 허용</li>
 *   <li>{@code Access-Control-Allow-Methods} — POST 포함 (login/reissue 호출용)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightIntegrationTest extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_for_login_returns_cors_headers_with_credentials_true() throws Exception {
        mockMvc.perform(options("/api/v1/users/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                // Preflight 자체는 본문 없이 200 또는 204를 기대
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void preflight_for_reissue_returns_cors_headers() throws Exception {
        mockMvc.perform(options("/api/v1/auth/reissue")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflight_with_disallowed_origin_does_not_return_allow_origin_header() throws Exception {
        // CORS 정책에 없는 origin에서 요청 시 Spring은 Allow-Origin 헤더를 응답에 포함하지 않는다.
        // 브라우저는 응답을 받아도 origin이 비어있어 cross-origin 요청을 차단한다.
        mockMvc.perform(options("/api/v1/users/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
