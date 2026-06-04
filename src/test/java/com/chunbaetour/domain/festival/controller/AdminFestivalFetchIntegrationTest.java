package com.chunbaetour.domain.festival.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalFetchResult;
import com.chunbaetour.domain.festival.service.FestivalFetchService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFestivalFetchIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private AccountRepository accountRepository;

    @MockitoBean private FestivalFetchService festivalFetchService;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("ADMIN 토큰 → 200 + fetched/created/skipped 반환")
    void admin_fetch_returns_200() throws Exception {
        String adminToken = adminToken();
        given(festivalFetchService.fetchNow()).willReturn(new FestivalFetchResult(5, 3, 2));

        mockMvc.perform(post("/api/v1/admin/festivals/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fetched").value(5))
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.skipped").value(2));
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seedAdmin("admin-fetch@test.com", PASSWORD, "관리자");
        seedFactory.seed("user-fetch@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user-fetch@test.com");

        mockMvc.perform(post("/api/v1/admin/festivals/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchant_token_forbidden() throws Exception {
        seedFactory.seedMerchant("merchant-fetch@test.com", PASSWORD, "상인");
        String merchantToken = loginAndGetToken("/api/v1/merchants/auth/login", "merchant-fetch@test.com");

        mockMvc.perform(post("/api/v1/admin/festivals/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("인증 없음 → 401")
    void no_auth_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/festivals/fetch"))
                .andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin-fetch@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin-fetch@test.com");
    }

    private String loginAndGetToken(String endpoint, String email) throws Exception {
        LoginRequest req = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
