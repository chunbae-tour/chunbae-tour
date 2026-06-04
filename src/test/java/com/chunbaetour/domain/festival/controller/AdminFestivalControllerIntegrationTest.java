package com.chunbaetour.domain.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 축제 CRUD API 통합 테스트 (KAN-96).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>POST 401(비인증) / 403(USER) / 201(ADMIN 성공) / 400(startDate &gt; endDate)</li>
 *   <li>PUT 200(성공) / 404(미존재) / 403(DELETED 수정)</li>
 *   <li>DELETE 200(성공, DB status=DELETED 확인) / 403(DELETED 재삭제)</li>
 *   <li>GET 목록 200(HIDDEN 포함 확인)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminFestivalControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/v1/admin/festivals";
    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private FestivalRepository festivalRepository;
    @Autowired private TokenIssuer tokenIssuer;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void ensureCleanState() {
        festivalRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        festivalRepository.deleteAll();
        accountRepository.findAll().stream()
                .map(Account::getId)
                .forEach(id -> stringRedisTemplate.delete("auth:refresh:" + id));
        accountRepository.deleteAll();
    }

    // ── POST: 권한 검증 ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST — 비인증 요청 → 401")
    void create_비인증_401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody("비인증 축제")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST — USER 토큰 요청 → 403 (ADMIN 권한 필요)")
    void create_USER_토큰_403() throws Exception {
        Account user = seedFactory.seed("user_" + UUID.randomUUID() + "@test.com", PASSWORD, "일반유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = tokenIssuer.issueAccess(user.getId(), user.getRole(), user.getEmail());

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody("권한없는 축제")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST — ADMIN 토큰, 유효한 요청 → 201 + festivalId 반환 (실제 로그인 흐름)")
    void create_ADMIN_성공_201() throws Exception {
        seedFactory.seedAdmin("admin-festival-create@test.com", PASSWORD, "관리자");
        String adminToken = loginAndGetToken("/api/v1/admin/auth/login", "admin-festival-create@test.com");

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody("테스트 축제")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("테스트 축제"))
                .andExpect(jsonPath("$.data.festivalId").value(Matchers.greaterThan(0))) // JsonPath parses small ints as Integer
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST — startDate가 endDate 이후 → 400 (도메인 불변식 위반)")
    void create_startDate_endDate_역전_400() throws Exception {
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "잘못된 날짜 축제",
                  "region": "서울",
                  "address": "서울시 강남구",
                  "startDate": "2026-07-20",
                  "endDate": "2026-07-10",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004")); // INVALID_INPUT_VALUE — 도메인 불변식 위반
    }

    @Test
    @DisplayName("POST — status=DELETED로 생성 요청 → 400 (정책 위반)")
    void create_DELETED_상태_생성_400() throws Exception {
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "삭제 상태 생성",
                  "region": "서울",
                  "address": "서울시",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "DELETED"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    // ── PUT ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT — 수정 성공 → 200 + 변경된 이름 확인")
    void update_성공_200() throws Exception {
        Festival saved = festivalRepository.save(buildFestival("원본 축제", FestivalStatus.ACTIVE));
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "수정된 축제",
                  "region": "부산",
                  "address": "부산시 해운대구",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(put(BASE_URL + "/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("수정된 축제"));
    }

    @Test
    @DisplayName("PUT — 존재하지 않는 festivalId → 404")
    void update_없는_festivalId_404() throws Exception {
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "수정됨",
                  "region": "서울",
                  "address": "서울시",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(put(BASE_URL + "/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FESTIVAL_001"));
    }

    @Test
    @DisplayName("PUT — DELETED 축제 수정 시도 → 403 (FESTIVAL_002)")
    void update_DELETED_축제_403() throws Exception {
        Festival saved = festivalRepository.save(buildFestival("삭제된 축제", FestivalStatus.DELETED));
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "수정 시도",
                  "region": "서울",
                  "address": "서울시",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(put(BASE_URL + "/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FESTIVAL_002"));
    }

    @Test
    @DisplayName("PUT — status=DELETED 요청 → 400 (delete() 우회 차단)")
    void update_status_DELETED_요청_400() throws Exception {
        Festival saved = festivalRepository.save(buildFestival("수정 대상 축제", FestivalStatus.ACTIVE));
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        String body = """
                {
                  "name": "삭제 우회 시도",
                  "region": "서울",
                  "address": "서울시",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "DELETED"
                }
                """;

        mockMvc.perform(put(BASE_URL + "/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE — soft delete 성공 → 200, DB status=DELETED 확인")
    void delete_성공_200() throws Exception {
        Festival saved = festivalRepository.save(buildFestival("삭제할 축제", FestivalStatus.ACTIVE));
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        mockMvc.perform(delete(BASE_URL + "/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        Festival afterDelete = festivalRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterDelete.getStatus()).isEqualTo(FestivalStatus.DELETED);
    }

    @Test
    @DisplayName("DELETE — DELETED 상태 재삭제 시도 → 403 (FESTIVAL_002)")
    void delete_DELETED_축제_403() throws Exception {
        Festival saved = festivalRepository.save(buildFestival("이미 삭제된 축제", FestivalStatus.DELETED));
        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        mockMvc.perform(delete(BASE_URL + "/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FESTIVAL_002"));
    }

    // ── GET 목록 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 — ACTIVE + HIDDEN 모두 반환, DELETED는 제외")
    void getAdminList_HIDDEN_포함_200() throws Exception {
        festivalRepository.save(buildFestival("공개 축제", FestivalStatus.ACTIVE));
        festivalRepository.save(buildFestival("비공개 축제", FestivalStatus.HIDDEN));
        festivalRepository.save(buildFestival("삭제된 축제", FestivalStatus.DELETED));

        Account admin = createAdmin();
        String adminToken = tokenIssuer.issueAccess(admin.getId(), admin.getRole(), admin.getEmail());

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(2)) // ACTIVE + HIDDEN, DELETED 제외
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Account createAdmin() {
        return seedFactory.seedAdmin("admin_" + UUID.randomUUID() + "@test.com", PASSWORD, "관리자");
    }

    private String validCreateBody(String name) {
        return """
                {
                  "name": "%s",
                  "region": "서울",
                  "address": "서울시 강남구 테헤란로 1",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-10",
                  "status": "ACTIVE"
                }
                """.formatted(name);
    }

    private Festival buildFestival(String name, FestivalStatus status) {
        Festival f = Festival.create(name, null, "서울", "서울시 강남구",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10),
                null, null, status == FestivalStatus.DELETED ? FestivalStatus.ACTIVE : status);
        if (status == FestivalStatus.DELETED) {
            f.delete();
        }
        return f;
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
