package com.chunbaetour.domain.store.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/store/products, GET /api/v1/store/products/{productId} 컨트롤러 테스트.
 * HTTP 상태, 파라미터 유효성(@Min/@Max), 응답 JSON 구조, 에러 포맷 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest extends AbstractIntegrationTest {

    private static final String LIST_ENDPOINT = "/api/v1/store/products";
    private static final String DETAIL_ENDPOINT = "/api/v1/store/products/";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
        redisTemplate.delete(redisTemplate.keys("product:*"));
    }

    @Test
    @DisplayName("상품 목록 — 토큰 없이 빈 목록 200 + cursor 구조 검증 (공개 API)")
    void getProducts_without_token_returns200_empty_list() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.size").value(0));
    }

    @Test
    @DisplayName("상품 목록 — size=0 요청 시 400 COMMON_002 (파라미터 @Min 검증)")
    void getProducts_size0_returns400() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT + "?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("상품 목록 — size=101 요청 시 400 COMMON_002 (파라미터 @Max 검증)")
    void getProducts_size101_returns400() throws Exception {
        mockMvc.perform(get(LIST_ENDPOINT + "?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("상품 상세 — 존재하지 않는 상품 404 STORE_001 (공개 API)")
    void getProduct_notFound_returns404() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT + "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_001"));
    }

    @Test
    @DisplayName("상품 상세 — 정상 조회 200 + 응답 JSON 구조 검증 (공개 API)")
    void getProduct_found_returns200() throws Exception {
        Product product = productRepository.save(Product.builder()
                .name("테스트상품")
                .category("한식")
                .price(10_000L)
                .stock(5)
                .originalStock(10)
                .status(ProductStatus.ON_SALE)
                .build());

        mockMvc.perform(get(DETAIL_ENDPOINT + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.name").value("테스트상품"))
                .andExpect(jsonPath("$.data.price").value(10_000))
                .andExpect(jsonPath("$.data.stock").value(5))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }
}
