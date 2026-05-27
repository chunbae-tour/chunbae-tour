package com.chunbaetour.domain.store.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /api/v1/store/products, GET /api/v1/store/products/{productId} 컨트롤러 테스트.
 * HTTP 상태, 파라미터 유효성(@Min/@Max), 응답 JSON 구조, 에러 포맷 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest extends AbstractIntegrationTest {

    private static final String LIST_ENDPOINT = "/api/v1/store/products";
    private static final String DETAIL_ENDPOINT = "/api/v1/store/products/";
    private static final String EMAIL = "product-test@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "상품테스터";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 목록 — 토큰 없이 접근 시 401 AUTH_006")
    void getProducts_without_token_returns401() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("상품 목록 — 인증 후 빈 목록 200 + cursor 구조 검증")
    void getProducts_authenticated_returns200_empty_list() throws Exception {
        signup();
        String token = login();

        mockMvc.perform(get(LIST_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.size").value(0));
    }

    @Test
    @DisplayName("상품 목록 — size=0 요청 시 400 COMMON_002 (파라미터 @Min 검증)")
    void getProducts_size0_returns400() throws Exception {
        signup();
        String token = login();

        mockMvc.perform(get(LIST_ENDPOINT + "?size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("상품 목록 — size=101 요청 시 400 COMMON_002 (파라미터 @Max 검증)")
    void getProducts_size101_returns400() throws Exception {
        signup();
        String token = login();

        mockMvc.perform(get(LIST_ENDPOINT + "?size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("상품 상세 — 토큰 없이 접근 시 401 AUTH_006")
    void getProduct_without_token_returns401() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT + "99999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("상품 상세 — 존재하지 않는 상품 404 STORE_001")
    void getProduct_notFound_returns404() throws Exception {
        signup();
        String token = login();

        mockMvc.perform(get(DETAIL_ENDPOINT + "99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_001"));
    }

    @Test
    @DisplayName("상품 상세 — 정상 조회 200 + 응답 JSON 구조 검증")
    void getProduct_found_returns200() throws Exception {
        signup();
        String token = login();

        Product product = productRepository.save(Product.builder()
                .name("테스트상품")
                .category("한식")
                .price(10_000L)
                .stock(5)
                .originalStock(10)
                .status(ProductStatus.ON_SALE)
                .build());

        mockMvc.perform(get(DETAIL_ENDPOINT + product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.name").value("테스트상품"))
                .andExpect(jsonPath("$.data.price").value(10_000))
                .andExpect(jsonPath("$.data.stock").value(5))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(EMAIL, PASSWORD, NICKNAME))))
                .andExpect(status().isCreated());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
