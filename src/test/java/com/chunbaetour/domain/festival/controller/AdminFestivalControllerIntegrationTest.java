package com.chunbaetour.domain.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 축제 관리 audit 통합 테스트 (Admin Epic KAN-177 S08, KAN-215).
 *
 * <p>축제 admin CRUD endpoint 자체는 KAN-95(#301)가 이미 구축 — 본 슬라이스는 거기에 {@code @LogAdminAction}
 * 감사 wiring을 더한 것이다. 따라서 검증 초점은 <b>등록/수정/삭제 시 {@code admin_action_logs}에 FESTIVAL_*
 * 액션이 기록되는지</b>다(POST는 S07 PLACE_CREATE처럼 {@code returnIdField}로 생성 id를 targetId에 기록).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminFestivalControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private FestivalRepository festivalRepository;
    @Autowired private StringRedisTemplate redis;

    // 앞뒤 양쪽 정리 — 다른 테스트가 남긴 잔여 상태로 인한 오염 방지(#323 패턴).
    @BeforeEach
    void setUp() {
        cleanState();
    }

    @AfterEach
    void cleanup() {
        cleanState();
    }

    private void cleanState() {
        adminActionLogRepository.deleteAll();
        festivalRepository.deleteAll();
        accountRepository.deleteAll();
        // 로그인이 Redis에 refresh 토큰을 남기므로 SCAN으로 정리(JVM 공유 컨테이너 — 클래스 간 키 누수 방지).
        deleteByPrefix("auth:refresh:*");
        deleteByPrefix("auth:blacklist:*");
    }

    private void deleteByPrefix(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("POST 등록 → 201 + audit FESTIVAL_CREATE(targetId=생성 id, returnIdField)")
    void create_returns_201_and_audit_targetId_from_return() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"name":"춘천마임축제","region":"강원 춘천","address":"공지천 일원",
                 "startDate":"2026-05-23","endDate":"2026-05-29"}
                """;

        mockMvc.perform(post("/api/v1/admin/festivals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("춘천마임축제"));

        Long createdId = festivalRepository.findAll().getFirst().getId();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.FESTIVAL_CREATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.FESTIVAL);
        assertThat(log.getTargetId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("PUT 수정 → 200 + audit FESTIVAL_UPDATE(targetId=festivalId)")
    void update_returns_200_and_audit() throws Exception {
        String adminToken = adminToken();
        Festival festival = saveFestival("원본축제", FestivalStatus.ACTIVE);
        String json = """
                {"name":"수정된축제","region":"강원 춘천","address":"공지천 일원",
                 "startDate":"2026-05-23","endDate":"2026-05-29","status":"ACTIVE"}
                """;

        mockMvc.perform(put("/api/v1/admin/festivals/" + festival.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된축제"));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.FESTIVAL_UPDATE);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(festival.getId());
    }

    @Test
    @DisplayName("DELETE → status DELETED + audit FESTIVAL_DELETE(targetId=festivalId)")
    void delete_softDelete_and_audit() throws Exception {
        String adminToken = adminToken();
        Festival festival = saveFestival("삭제대상축제", FestivalStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/admin/festivals/" + festival.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(festivalRepository.findById(festival.getId()).orElseThrow().getStatus())
                .isEqualTo(FestivalStatus.DELETED);

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.FESTIVAL_DELETE);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(festival.getId());
    }

    @Test
    @DisplayName("PUT 이미 DELETED → 403 FESTIVAL_002 + audit 미기록")
    void update_alreadyDeleted_returns_403_no_audit() throws Exception {
        String adminToken = adminToken();
        Festival festival = saveFestival("삭제된축제", FestivalStatus.DELETED);
        String json = """
                {"name":"부활시도","region":"강원 춘천","address":"공지천 일원",
                 "startDate":"2026-05-23","endDate":"2026-05-29","status":"ACTIVE"}
                """;

        mockMvc.perform(put("/api/v1/admin/festivals/" + festival.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FESTIVAL_002"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(post("/api/v1/admin/festivals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Festival saveFestival(String name, FestivalStatus status) {
        // 항상 ACTIVE로 생성 후 전이 — #323에서 Festival.create()가 ACTIVE만 허용하도록 바뀌어도 안전.
        Festival f = Festival.create(
                name, "설명", "강원 춘천", "공지천 일원",
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 29),
                null, null, FestivalStatus.ACTIVE);
        if (status == FestivalStatus.DELETED) {
            f.delete();
        }
        return festivalRepository.save(f);
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
