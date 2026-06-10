package com.chunbaetour.domain.shop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.admin.audit.AdminActionLog;
import com.chunbaetour.domain.admin.audit.AdminActionLogRepository;
import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
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
 * 관리자 가게-장소 연결 API 통합 테스트 (KAN-217).
 * PATCH /api/v1/admin/shops/{shopId}/place — MVC/AOP(@LogAdminAction)/Security 흐름 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminShopPlaceControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private ShopRepository shopRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private AdminActionLogRepository adminActionLogRepository;

    @AfterEach
    void cleanup() {
        adminActionLogRepository.deleteAll();
        shopRepository.deleteAll();
        placeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("ADMIN + placeId 지정 → 200 + shop.placeId 변경 + SHOP_UPDATE audit 기록")
    void linkPlace_success() throws Exception {
        String token = adminToken();
        Place place = seedPlace(PlaceStatus.ACTIVE);
        Shop shop = seedShop();

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": " + place.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopId").value(shop.getId()))
                .andExpect(jsonPath("$.data.placeId").value(place.getId()))
                .andExpect(jsonPath("$.data.placeName").value("광장시장"))
                .andExpect(jsonPath("$.data.linked").value(true));

        Shop updated = shopRepository.findById(shop.getId()).orElseThrow();
        assertThat(updated.getPlaceId()).isEqualTo(place.getId());

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getActionType()).isEqualTo(AdminActionType.SHOP_UPDATE);
        assertThat(logs.get(0).getTargetType()).isEqualTo(AdminTargetType.SHOP);
        assertThat(logs.get(0).getTargetId()).isEqualTo(shop.getId());
    }

    @Test
    @DisplayName("ADMIN + placeId=null → 200 + 연결 해제")
    void unlinkPlace_success() throws Exception {
        String token = adminToken();
        Place place = seedPlace(PlaceStatus.ACTIVE);
        Shop shop = seedShop();
        ReflectionTestUtils.setField(shop, "placeId", place.getId());
        shopRepository.save(shop);

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopId").value(shop.getId()))
                .andExpect(jsonPath("$.data.linked").value(false));

        Shop updated = shopRepository.findById(shop.getId()).orElseThrow();
        assertThat(updated.getPlaceId()).isNull();
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user-place@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user-place@test.com");
        Shop shop = seedShop();

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("placeId 음수 → 400")
    void negativePlaceId_badRequest() throws Exception {
        String token = adminToken();
        Shop shop = seedShop();

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 placeId → PLACE_NOT_FOUND")
    void nonExistentPlaceId_notFound() throws Exception {
        String token = adminToken();
        Shop shop = seedShop();

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_001"));
    }

    @Test
    @DisplayName("DELETED 상태 Place 연결 시도 → PLACE_NOT_FOUND")
    void deletedPlace_notFound() throws Exception {
        String token = adminToken();
        Place deleted = seedPlace(PlaceStatus.DELETED);
        Shop shop = seedShop();

        mockMvc.perform(patch("/api/v1/admin/shops/" + shop.getId() + "/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\": " + deleted.getId() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_001"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Shop seedShop() {
        Shop shop = Shop.builder()
                .userId(9001L).applicationId(System.nanoTime())
                .shopName("테스트가게").category("FOOD")
                .address("서울시 강남구").lat(new BigDecimal("37.4979000"))
                .lng(new BigDecimal("127.0276000"))
                .phone("02-0000-0000").description("테스트 소개").build();
        return shopRepository.save(shop);
    }

    private Place seedPlace(PlaceStatus status) {
        Place place = Place.builder()
                .name("광장시장").category(PlaceCategory.TRADITIONAL_MARKET)
                .description("전통시장").address("서울시 종로구 창경궁로 88")
                .lat(new BigDecimal("37.5700390")).lng(new BigDecimal("126.9996030"))
                .build();
        if (status != PlaceStatus.ACTIVE) {
            ReflectionTestUtils.setField(place, "status", status);
        }
        return placeRepository.save(place);
    }

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin-place@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin-place@test.com");
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
