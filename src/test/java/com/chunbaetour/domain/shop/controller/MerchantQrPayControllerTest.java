package com.chunbaetour.domain.shop.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.service.QrPayService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code MerchantQrPayController.getPendingQrPayments} 통합 테스트 (KAN-184).
 *
 * <p>검증 범위: 인증/인가 + happy path 응답 코드.
 * 서비스는 {@link MockitoBean}으로 stub — service 구현은 {@code QrPayServiceTest}에서 별도 커버.
 * SecurityConfig의 {@code hasRole(MERCHANT)} 매핑이 변경됐을 때 회귀를 감지한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MerchantQrPayControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @MockitoBean
    private QrPayService qrPayService;

    private static final long MERCHANT_USER_ID = 1L;
    private static final long USER_USER_ID = 2L;
    private static final long ADMIN_USER_ID = 3L;
    private static final String URL = "/api/v1/merchants/me/qr-payments/pending";

    @Test
    @DisplayName("MERCHANT 토큰 → 200, 서비스 반환 목록 응답")
    void getPendingQrPayments_whenMerchant_returns200() throws Exception {
        given(qrPayService.getPendingQrPayments(MERCHANT_USER_ID)).willReturn(List.of());

        String token = tokenIssuer.issueAccess(MERCHANT_USER_ID, Role.MERCHANT, "merchant@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("미인증 → 401 AUTH_006, service 미호출")
    void getPendingQrPayments_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));

        verifyNoInteractions(qrPayService);
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007, service 미호출")
    void getPendingQrPayments_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(USER_USER_ID, Role.USER, "user@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        verifyNoInteractions(qrPayService);
    }

    @Test
    @DisplayName("ADMIN 토큰 → 403 AUTH_007, service 미호출")
    void getPendingQrPayments_whenAdmin_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(ADMIN_USER_ID, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        verifyNoInteractions(qrPayService);
    }
}
