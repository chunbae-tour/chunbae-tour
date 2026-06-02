package com.chunbaetour.domain.admin.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.admin.audit.AdminActionLog;
import com.chunbaetour.domain.admin.audit.AdminActionLogRepository;
import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 가게 관리 API 통합 테스트 (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>GET 상세 200 / PATCH status=SUSPENDED 200 → 공개 조회 미노출</li>
 *   <li>PATCH status=ACTIVE 200(복구) → 공개 재노출 + 상인 GET 반영</li>
 *   <li>PATCH description만 200 / 빈 body 미변경 200</li>
 *   <li>미존재 404 / CLOSED 가게 status 전이 403(SHOP_INACTIVE, hide()/activate() 대칭) /
 *       status=CLOSED 지정 400 / USER·MERCHANT 토큰 403</li>
 *   <li>PATCH → admin_action_logs 1건 (SHOP_UPDATE, targetId=shopId)</li>
 * </ul>
 *
 * <p>결정 B: HIDDEN 미도입, 숨김=SUSPENDED 재사용. CLOSED 전이 거부 예외는 hide()와 대칭으로 SHOP_INACTIVE(403).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminShopControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private ShopRepository shopRepository;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private TokenIssuer tokenIssuer;

    @AfterEach
    void cleanup() {
        adminActionLogRepository.deleteAll();
        shopRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 정상 흐름 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 상세 → 200 + 가게 정보")
    void getShop_returns_200() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(shop.getId()))
                .andExpect(jsonPath("$.data.shopName").value("테스트가게"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH status=SUSPENDED → 200 숨김 + 공개 조회 미노출 + audit 1건")
    void patch_suspend_hides_from_public_and_records_audit() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(shopRepository.findById(shop.getId()).orElseThrow().getStatus())
                .isEqualTo(ShopStatus.SUSPENDED);

        // 공개 조회(GET /api/v1/shops/{shopId})는 SUSPENDED 가게를 SHOP_001로 차단
        mockMvc.perform(get("/api/v1/shops/" + shop.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.SHOP_UPDATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.SHOP);
        assertThat(log.getTargetId()).isEqualTo(shop.getId());
    }

    @Test
    @DisplayName("PATCH status=ACTIVE → SUSPENDED 가게 복구 + 공개 재노출 + 상인 GET 반영")
    void patch_activate_restores_visibility() throws Exception {
        String adminToken = adminToken();
        Account merchant = seedFactory.seedMerchant("merchant-restore@test.com", PASSWORD, "복구상인");
        Shop shop = seedShopFor(merchant.getId(), ShopStatus.ACTIVE);
        shop.hide();
        shopRepository.save(shop); // SUSPENDED

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 공개 조회 재노출
        mockMvc.perform(get("/api/v1/shops/" + shop.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopId").value(shop.getId()));

        // 상인 본인 페이지 반영
        String merchantToken = tokenIssuer.issueAccess(merchant.getId(), Role.MERCHANT, merchant.getEmail());
        mockMvc.perform(get("/api/v1/merchants/me/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH description만 → 200 + 상인 페이지 즉시 반영")
    void patch_description_only_reflects_to_merchant() throws Exception {
        String adminToken = adminToken();
        Account merchant = seedFactory.seedMerchant("merchant-desc@test.com", PASSWORD, "소개상인");
        Shop shop = seedShopFor(merchant.getId(), ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"운영자 수정 소개\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("운영자 수정 소개"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String merchantToken = tokenIssuer.issueAccess(merchant.getId(), Role.MERCHANT, merchant.getEmail());
        mockMvc.perform(get("/api/v1/merchants/me/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("운영자 수정 소개"));
    }

    @Test
    @DisplayName("PATCH 빈 body → 200 미변경")
    void patch_empty_body_no_change() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("테스트 소개"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH phone 공백-only → 400 (공백 차단)")
    void patch_blank_only_phone_returns_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH description 공백-only → 400 (공백 차단)")
    void patch_blank_only_description_returns_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH operatingHours 공백-only → 400 (공백 차단)")
    void patch_blank_only_operatingHours_returns_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatingHours\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH description 멀티라인(개행 포함) → 200 (DOTALL 회귀 가드)")
    void patch_multiline_description_returns_200() throws Exception {
        // @Pattern이 (?s) 없이 ".*\\S.*"만 쓰면 '.'이 개행을 매치하지 못해 멀티라인 소개가 거부됐다(회귀).
        // 내용 있는 멀티라인은 정상 입력이므로 200이어야 한다.
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"첫 줄 소개\\n둘째 줄 소개\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("첫 줄 소개\n둘째 줄 소개"));
    }

    // ── 에러/접근 제어 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("미존재 shopId → 404 SHOP_001 + audit 미기록")
    void patch_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/admin/shops/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("CLOSED 가게 status 전이 → 403 SHOP_005 (hide()/activate() 대칭 가드)")
    void patch_closed_shop_status_returns_403() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.CLOSED);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHOP_005"));
    }

    @Test
    @DisplayName("status=CLOSED 직접 지정 → 400 COMMON_004")
    void patch_status_closed_rejected_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchant_token_forbidden() throws Exception {
        Account merchant = seedFactory.seedMerchant("merchant-forbid@test.com", PASSWORD, "권한없음상인");
        Shop shop = seedShopFor(merchant.getId(), ShopStatus.ACTIVE);
        String merchantToken = tokenIssuer.issueAccess(merchant.getId(), Role.MERCHANT, merchant.getEmail());

        mockMvc.perform(get("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user-forbid@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user-forbid@test.com");
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/shops/" + shop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Shop seedShop(ShopStatus status) {
        return seedShopFor(9001L, status);
    }

    private Shop seedShopFor(Long userId, ShopStatus status) {
        Shop shop = Shop.builder()
                .userId(userId).applicationId(System.nanoTime())
                .shopName("테스트가게").category("FOOD")
                .address("서울시 강남구").lat(new BigDecimal("37.4979000"))
                .lng(new BigDecimal("127.0276000"))
                .phone("02-0000-0000").description("테스트 소개").build();
        if (status == ShopStatus.SUSPENDED) {
            shop.hide();
        } else if (status == ShopStatus.CLOSED) {
            ReflectionTestUtils.setField(shop, "status", ShopStatus.CLOSED);
        }
        return shopRepository.save(shop);
    }

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin-shop@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin-shop@test.com");
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
