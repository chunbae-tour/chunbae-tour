package com.chunbaetour.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator endpoint 권한 매핑 검증 (#149).
 *
 * <p>SecurityConfig의 actuator 권한:
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — permitAll (LB health check)</li>
 *   <li>{@code /actuator/prometheus} — IP allowlist (loopback only). 외부 IP는 deny.</li>
 *   <li>{@code /actuator/**} (그 외) — denyAll.</li>
 * </ul>
 *
 * <p><b>Spring Security 응답 코드</b>: 익명 사용자가 deny된 endpoint 호출 시 {@code RestAuthenticationEntryPoint}가
 * 발동되어 401 AUTH_006으로 응답한다 ("인증 요구"로 해석). 인증된 사용자 대상으로는 403 응답. 본 테스트는
 * 익명 사용자만 검증하므로 deny = 401 패턴.
 *
 * <p>운영(application-prod.yml)에서는 별도 management.server.port(9090) + loopback 바인딩으로 이중 보호.
 * 본 테스트는 SecurityConfig의 IP allowlist만 검증한다 (management port 분리는 통합 테스트 환경에서
 * 시뮬레이션이 어려워 제외).
 *
 * <p><b>prometheus endpoint 등록</b>: 테스트 환경에서는 prometheus endpoint 자체가 등록되지 않을 수 있어
 * (Micrometer registry 자동 구성 조건), 권한 통과 케이스는 status 자체보다 "권한 차단(401)이 아닌지"만 검증한다.
 *
 * <p>release blocker #149 회귀 가드.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/health는 비인증 + 외부 IP에서도 접근 가능 (LB health check)")
    void health_isPermitAll_evenFromExternalIp() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(req -> { req.setRemoteAddr("203.0.113.1"); return req; }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/info는 비인증 + 외부 IP에서도 접근 가능")
    void info_isPermitAll_evenFromExternalIp() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .with(req -> { req.setRemoteAddr("203.0.113.1"); return req; }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/prometheus loopback(127.0.0.1) 호출 시 권한 통과 (401이 아님)")
    void prometheus_isNotBlockedFromLoopback() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andReturn();
        // endpoint 등록 안 된 환경에서는 404/500이 정상 — 핵심은 권한 차단(401)이 발생하지 않는 것
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("/actuator/prometheus IPv6 loopback(::1) 호출 시에도 권한 통과 (401이 아님)")
    void prometheus_isNotBlockedFromIpv6Loopback() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("::1"); return req; }))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("/actuator/prometheus 외부 IP(203.0.113.x) 호출 시 401 차단 — #149 release blocker 회귀 가드")
    void prometheus_isBlockedFromExternalIp() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("203.0.113.1"); return req; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/prometheus 사설망 IP(10.x.x.x) 호출 시에도 401 차단 — loopback만 허용 정책")
    void prometheus_isBlockedFromPrivateNetworkIp() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("10.0.0.5"); return req; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/env는 denyAll — loopback에서도 401 차단 (민감 정보 노출 차단)")
    void env_isDeniedEvenFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/beans는 denyAll — loopback에서도 401 차단")
    void beans_isDeniedEvenFromLoopback() throws Exception {
        mockMvc.perform(get("/actuator/beans")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andExpect(status().isUnauthorized());
    }
}
