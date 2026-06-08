package com.chunbaetour.domain.admin.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 관광지 관리 API 통합 테스트 (Admin Epic KAN-177 S07).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>목록(200, keyword/category 필터, cursor 페이징) / 등록(201) / 수정(200, partial — 미지정 필드 보존) / 삭제(204)</li>
 *   <li>삭제 후 목록·사용자 검색에서 제외 (status=DELETED soft delete)</li>
 *   <li>등록/수정/삭제 → {@code admin_action_logs} 자동 기록. PATCH/DELETE는 targetId=placeId,
 *       POST는 {@code returnIdField="id"}로 응답 본문의 생성 id를 targetId에 기록(S01 aspect 확장)</li>
 *   <li>이미 DELETED인 관광지 수정/삭제 → 409 PLACE_015(멱등 가드) + audit 미기록</li>
 *   <li>입력 검증: 빈 PATCH·공백-only·필수 누락·imageUrls/tags 비-JSON-배열 → 400, 잘못된 cursor → 400(COMMON_008)</li>
 *   <li>미존재 placeId 수정/삭제 → 404 PLACE_001</li>
 *   <li>USER/MERCHANT 토큰 403(AUTH_007)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPlaceControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private PlaceRepository placeRepository;

    @AfterEach
    void cleanup() {
        adminActionLogRepository.deleteAll();
        placeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 → 200 + ACTIVE/HIDDEN 포함, DELETED 제외")
    void getPlaces_returns_200_excludes_deleted() throws Exception {
        String adminToken = adminToken();
        savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);
        savePlace("남대문시장", PlaceCategory.TRADITIONAL_MARKET, PlaceStatus.HIDDEN);
        savePlace("철거된곳", PlaceCategory.TOURIST_SPOT, PlaceStatus.DELETED);

        mockMvc.perform(get("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("GET 목록 keyword 필터 → 이름 부분일치만")
    void getPlaces_keyword_filter() throws Exception {
        String adminToken = adminToken();
        savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);
        savePlace("남대문시장", PlaceCategory.TRADITIONAL_MARKET, PlaceStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/places")
                        .param("keyword", "남대문")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("남대문시장"));
    }

    @Test
    @DisplayName("GET 목록 category 필터 → 해당 카테고리만")
    void getPlaces_category_filter() throws Exception {
        String adminToken = adminToken();
        savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);
        savePlace("남대문시장", PlaceCategory.TRADITIONAL_MARKET, PlaceStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/places")
                        .param("category", "TRADITIONAL_MARKET")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].category").value("TRADITIONAL_MARKET"));
    }

    @Test
    @DisplayName("GET 목록 cursor 페이징 → size+1 sentinel로 hasNext/nextCursor")
    void getPlaces_cursor_paging() throws Exception {
        String adminToken = adminToken();
        for (int i = 0; i < 3; i++) {
            savePlace("관광지" + i, PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);
        }

        MvcResult first = mockMvc.perform(get("/api/v1/admin/places")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(first.getResponse().getContentAsString());
        String nextCursor = body.get("data").get("nextCursor").asString();

        mockMvc.perform(get("/api/v1/admin/places")
                        .param("size", "2")
                        .param("cursor", nextCursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    // ── 등록 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 등록 → 201 + Place ACTIVE 저장 + audit PLACE_CREATE(targetId=생성 id, returnIdField)")
    void createPlace_returns_201_and_audit_targetId_from_return() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"name":"불국사","category":"TOURIST_SPOT","address":"경주시 진현동",
                 "lat":35.7900,"lng":129.3320,"operatingHours":"07:00-18:00"}
                """;

        mockMvc.perform(post("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("불국사"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(placeRepository.findAll()).hasSize(1);
        Long createdId = placeRepository.findAll().getFirst().getId();

        // D: POST는 path 변수가 없지만 returnIdField="id"로 응답 본문의 id를 targetId에 기록한다.
        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.PLACE_CREATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.PLACE);
        assertThat(log.getTargetId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("POST imageUrls 비-JSON-배열 → 400 (형식 검증)")
    void createPlace_invalid_imageUrls_returns_400() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"name":"불국사","category":"TOURIST_SPOT","address":"경주시 진현동",
                 "lat":35.7900,"lng":129.3320,"imageUrls":"not-a-json-array"}
                """;

        mockMvc.perform(post("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST 등록 필수 누락(name 빈값) → 400")
    void createPlace_blank_name_returns_400() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"name":"","category":"TOURIST_SPOT","address":"주소","lat":35.0,"lng":129.0}
                """;

        mockMvc.perform(post("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ── 수정 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH 수정 → 200 + partial(미지정 필드 보존) + audit PLACE_UPDATE(targetId=placeId)")
    void updatePlace_partial_returns_200_and_audit() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"02-222-2222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("02-222-2222"))
                // 미지정 필드 보존
                .andExpect(jsonPath("$.data.name").value("경복궁"))
                .andExpect(jsonPath("$.data.address").value("서울 종로구"));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.PLACE_UPDATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.PLACE);
        assertThat(log.getTargetId()).isEqualTo(place.getId());
    }

    @Test
    @DisplayName("PATCH 빈 body({}) → 400 + audit 미기록")
    void updatePlace_empty_body_returns_400() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 공백-only 필드 → 400")
    void updatePlace_blank_field_returns_400() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH 미존재 placeId → 404 PLACE_001 + audit 미기록")
    void updatePlace_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/admin/places/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"02-000-0000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_001"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 이미 DELETED → 409 PLACE_015 + audit 미기록 (M1, deletePlace와 동일 가드)")
    void updatePlace_alreadyDeleted_returns_409() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.DELETED);

        mockMvc.perform(patch("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"02-222-2222\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_015"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH tags 비-JSON-배열 → 400 (M2, 형식 검증)")
    void updatePlace_invalid_tags_returns_400() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":\"not-a-json-array\"}"))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    // ── 삭제 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE → 204 + status DELETED + 목록 제외 + audit PLACE_DELETE")
    void deletePlace_returns_204_soft_delete_and_audit() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PlaceStatus.DELETED);

        // 목록에서 제외
        mockMvc.perform(get("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.PLACE_DELETE);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(place.getId());
    }

    @Test
    @DisplayName("DELETE 미존재 placeId → 404 PLACE_001 + audit 미기록")
    void deletePlace_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(delete("/api/v1/admin/places/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_001"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("DELETE 이미 DELETED → 409 PLACE_015 + audit 미기록 (멱등 가드)")
    void deletePlace_alreadyDeleted_returns_409() throws Exception {
        String adminToken = adminToken();
        Place place = savePlace("경복궁", PlaceCategory.TOURIST_SPOT, PlaceStatus.DELETED);

        mockMvc.perform(delete("/api/v1/admin/places/" + place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_015"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    // ── cursor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 잘못된 cursor → 400 COMMON_008")
    void getPlaces_invalidCursor_returns_400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("cursor", "!!!not-base64!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    // ── 접근 제어 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user@test.com", PASSWORD, "유저",
                com.chunbaetour.domain.auth.Role.USER, com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(get("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchant_token_forbidden() throws Exception {
        seedFactory.seedMerchant("merchant@test.com", PASSWORD, "상인");
        String merchantToken = loginAndGetToken("/api/v1/merchants/auth/login", "merchant@test.com");

        mockMvc.perform(get("/api/v1/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Place savePlace(String name, PlaceCategory category, PlaceStatus status) {
        org.locationtech.jts.geom.GeometryFactory gf = new org.locationtech.jts.geom.GeometryFactory();
        org.locationtech.jts.geom.Point location = gf.createPoint(new org.locationtech.jts.geom.Coordinate(126.9780, 37.5665));
        location.setSRID(4326);

        Place place = Place.builder()
                .name(name)
                .category(category)
                .description("테스트 설명")
                .address("서울 종로구")
                .location(location)
                .operatingHours("09:00-18:00")
                .build();
        if (status == PlaceStatus.HIDDEN) {
            place.hide();
        } else if (status == PlaceStatus.DELETED) {
            place.delete();
        }
        return placeRepository.save(place);
    }

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin@test.com");
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
