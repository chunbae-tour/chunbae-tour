package com.chunbaetour.domain.merchant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /api/v1/merchants/apply 인증/인가 회귀 테스트.
 * SecurityConfig hasRole("USER") 적용 여부를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MerchantApplySecurityTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/merchants/apply";
    private static final String PASSWORD = "Pa$$w0rd1!";

    private static final String VALID_BODY = """
            {
              "shopName": "테스트가게",
              "businessNumber": "101-81-34618",
              "category": "한식",
              "address": "서울시 강남구 테헤란로 1",
              "lat": 37.5665,
              "lng": 126.9780
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountSeedFactory seedFactory;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("토큰 없음 → 401")
    void noToken_returns401() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER 토큰 + 빈 바디 → 400 (인가 통과, 유효성 실패)")
    void userToken_emptyBody_returns400() throws Exception {
        seedFactory.seed("user@example.com", PASSWORD, "유저닉", Role.USER, AccountStatus.ACTIVE);
        String token = loginAndGetToken("/api/v1/users/auth/login", "user@example.com");

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchantToken_returns403() throws Exception {
        seedFactory.seedMerchant("merchant@example.com", PASSWORD, "상인닉");
        String token = loginAndGetToken("/api/v1/merchants/auth/login", "merchant@example.com");

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("ADMIN 토큰 → 403 AUTH_007")
    void adminToken_returns403() throws Exception {
        seedFactory.seedAdmin("admin@example.com", PASSWORD, "관리자닉");
        String token = loginAndGetToken("/api/v1/admin/auth/login", "admin@example.com");

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    private String loginAndGetToken(String loginEndpoint, String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post(loginEndpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
