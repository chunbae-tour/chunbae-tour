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
 * <p><b>MockMvc 검증 범위 한계</b>: MockMvc는 Spring Security 필터 체인만 통과시키므로
 * {@code management.server.port=9090} 같은 서블릿 컨테이너 레벨 포트 바인딩은 검증하지 못한다.
 * 본 테스트는 SecurityConfig의 IP allowlist 동작만 검증한다 — 옵션 A(포트 분리)는 운영
 * 인프라 단에서 별도 검증 필요(예: 운영 배포 후 외부 IP에서 :9090/actuator/prometheus 호출 → 응답 없음 확인).
 *
 * <p><b>prometheus endpoint 등록</b>: 테스트 환경에서는 prometheus endpoint 자체가 등록되지 않을 수 있어
 * (Micrometer registry 자동 구성 조건), 권한 통과 케이스는 status 자체보다 허용 응답 범위로 검증한다.
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
    @DisplayName("/actuator/prometheus는 loopback(127.0.0.1)에서 호출 시 권한 통과 (401 차단되지 않음)")
    void prometheus_isNotBlockedFromLoopback() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andReturn();
        // 권한 통과 검증: 401(차단)이 아니어야 함.
        // 허용 응답 범위: 200(prometheus endpoint 등록됨) / 404(endpoint 미등록 — micrometer-registry-prometheus
        // auto-config 비활성화 시) / 500(NoResourceFoundException를 GlobalExceptionHandler가 변환).
        // 그 외 값이면 회귀로 판단.
        assertThat(result.getResponse().getStatus()).isIn(200, 404, 500);
    }

    @Test
    @DisplayName("/actuator/prometheus는 IPv6 loopback(::1)에서도 호출 시 권한 통과 (401 차단되지 않음)")
    void prometheus_isNotBlockedFromIpv6Loopback() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("::1"); return req; }))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(200, 404, 500);
    }

    @Test
    @DisplayName("/actuator/prometheus는 외부 IP(203.0.113.x)에서 호출 시 401 차단 — #149 release blocker 회귀 가드")
    void prometheus_isBlockedFromExternalIp() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> { req.setRemoteAddr("203.0.113.1"); return req; }))
                .andExpect(status().isUnauthorized());
    }

    /**
     * ⚠️ 본 케이스는 "loopback만 허용" 정책 가정에 종속.
     * 운영 Prometheus 클러스터가 VPC 내부 IP에서 직접 scrape하도록 인프라가 결정되면
     * SecurityConfig hasIpAddress 정책에 VPC CIDR(예: 10.0.0.0/8)이 추가되며, 본 테스트는 깨진다.
     * 그 경우 본 케이스를 "허용 CIDR 외부 IP 차단"으로 재정의 또는 삭제 필요.
     * application-prod.yml의 management.server.address TODO 주석과 함께 추적.
     */
    @Test
    @DisplayName("/actuator/prometheus는 사설망 IP(10.x.x.x)에서도 호출 시 401 차단 — loopback만 허용 정책")
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
