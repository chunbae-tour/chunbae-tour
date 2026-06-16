package com.chunbaetour.domain.store.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.UUID;
import org.hamcrest.Matchers;
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
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private TokenIssuer tokenIssuer;

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

    @Test
    @DisplayName("ADMIN 토큰으로 상품 목록 조회 → 200, HIDDEN 포함 전체 반환 (KAN-300)")
    void getProducts_asAdmin_returns200WithHidden() throws Exception {
        saveProduct("판매중 쿠폰", ProductStatus.ON_SALE);
        saveProduct("숨김 쿠폰", ProductStatus.HIDDEN);
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                // 공개 목록과 달리 HIDDEN 상품이 응답에 포함되는지 검증
                .andExpect(jsonPath("$.data.content[*].status", Matchers.hasItem("HIDDEN")));
    }

    @Test
    @DisplayName("ADMIN + status=HIDDEN 필터 → 숨김 상품만 반환 (enum 파라미터 바인딩 검증, KAN-300)")
    void getProducts_asAdmin_statusFilter_returnsOnlyHidden() throws Exception {
        saveProduct("판매중 쿠폰", ProductStatus.ON_SALE);
        saveProduct("숨김 쿠폰", ProductStatus.HIDDEN);
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("status", "HIDDEN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[0].name").value("숨김 쿠폰"));
    }

    @Test
    @DisplayName("ADMIN + category 필터 → 해당 카테고리 상품만 반환 (KAN-300)")
    void getProducts_asAdmin_categoryFilter_returnsOnlyMatching() throws Exception {
        saveProduct("음식 쿠폰", ProductStatus.ON_SALE, "EXPERIENCE");
        saveProduct("일반 쿠폰", ProductStatus.ON_SALE, "DISCOUNT_COUPON");
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("category", "EXPERIENCE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("음식 쿠폰"));
    }

    @Test
    @DisplayName("ADMIN + 매칭 상품 없는 유효 category 필터 → 200 + 빈 content (KAN-300)")
    void getProducts_asAdmin_noMatch_returnsEmptyContent() throws Exception {
        saveProduct("일반 쿠폰", ProductStatus.ON_SALE, "DISCOUNT_COUPON");
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("category", "TOUR_PASS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("ADMIN 페이징 계약 — size 초과 상품 시 content=size, hasNext=true, nextCursor 발급 (KAN-300)")
    void getProducts_asAdmin_pagingContract_hasNextAndCursor() throws Exception {
        saveProduct("상품A", ProductStatus.ON_SALE);
        saveProduct("상품B", ProductStatus.ON_SALE);
        saveProduct("상품C", ProductStatus.ON_SALE);
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                // size는 실제 반환 개수가 아니라 요청 size를 echo (팀 표준)
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    @DisplayName("ADMIN size=0 요청 → 400 COMMON_002 (@Min 검증)")
    void getProducts_asAdmin_size0_returns400() throws Exception {
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("ADMIN size=101 요청 → 400 COMMON_002 (@Max 검증)")
    void getProducts_asAdmin_size101_returns400() throws Exception {
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("ADMIN 유효하지 않은 cursor → 400 COMMON_008 (INVALID_CURSOR)")
    void getProducts_asAdmin_invalidCursor_returns400() throws Exception {
        String token = adminToken();

        mockMvc.perform(get(ENDPOINT)
                        .param("cursor", "!!!")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    /** ADMIN 계정 seed 후 access 토큰 발급 — 로그인 흐름 없이 직접 발급 */
    private String adminToken() {
        Account admin = seedFactory.seedAdmin("admin_" + UUID.randomUUID() + "@test.com", PASSWORD, "관리자");
        return tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());
    }

    private void saveProduct(String name, ProductStatus status) {
        saveProduct(name, status, "DISCOUNT_COUPON");
    }

    private void saveProduct(String name, ProductStatus status, String category) {
        productRepository.save(Product.builder()
                .name(name).description("설명").category(ProductCategory.valueOf(category))
                .price(2000L).originalPrice(3000L).stock(status == ProductStatus.SOLD_OUT ? 0 : 10)
                .originalStock(10).imageUrls(null).merchantName("상인")
                .validityDays(30).status(status).maxPerPerson(5).build());
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
