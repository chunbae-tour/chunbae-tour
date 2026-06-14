package com.chunbaetour.domain.store.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.store.repository.ProductRepository;
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
 * AdminProductController 보안 테스트.
 * SecurityConfig: /api/v1/admin/** → ADMIN 권한 필수.
 * USER 역할로 접근 시 403 Forbidden 반환 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminProductControllerTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/admin/store/products";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "일반유저";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private AccountRepository accountRepository;

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("USER 역할로 상품 목록 조회 요청 시 403 Forbidden (KAN-300)")
    void getProducts_asUser_returns403() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER 역할로 상품 등록 요청 시 403 Forbidden")
    void createProduct_asUser_returns403() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER 역할로 상품 수정 요청 시 403 Forbidden")
    void updateProduct_asUser_returns403() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(patch(ENDPOINT + "/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER 역할로 상품 삭제 요청 시 403 Forbidden")
    void deleteProduct_asUser_returns403() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(delete(ENDPOINT + "/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비인증 접근 시 401 Unauthorized — GET/POST/PATCH/DELETE 모두 검증")
    void allEndpoints_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(ENDPOINT + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(ENDPOINT + "/1"))
                .andExpect(status().isUnauthorized());
    }

    private String loginAsUser() throws Exception {
        mockMvc.perform(post("/api/v1/users/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(EMAIL, PASSWORD, NICKNAME))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }
}
