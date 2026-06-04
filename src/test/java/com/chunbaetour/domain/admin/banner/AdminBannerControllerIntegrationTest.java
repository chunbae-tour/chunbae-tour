package com.chunbaetour.domain.admin.banner;

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
import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.repository.BannerRepository;
import com.chunbaetour.domain.banner.type.BannerStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
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
 * 운영자 배너 관리 API 통합 테스트 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>목록(200, status 필터, priority/id cursor 페이징) / 등록(201) / 수정(200, partial 보존) / 삭제(204)</li>
 *   <li>삭제 후 목록 제외 (status=DELETED soft delete)</li>
 *   <li>등록/수정/삭제 → {@code admin_action_logs} 자동 기록. PATCH/DELETE는 targetId=bannerId,
 *       POST는 {@code returnIdField="id"}로 응답 본문의 생성 id를 targetId에 기록</li>
 *   <li>이미 DELETED인 배너 수정/삭제 → 409 BANNER_002(멱등 가드) + audit 미기록</li>
 *   <li>입력 검증: 빈 PATCH·공백-only·필수 누락·날짜 역전 → 400, 잘못된 cursor → 400(COMMON_008)</li>
 *   <li>미존재 bannerId 수정/삭제 → 404 BANNER_001</li>
 *   <li>USER/MERCHANT 토큰 403(AUTH_007)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminBannerControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private BannerRepository bannerRepository;

    @AfterEach
    void cleanup() {
        adminActionLogRepository.deleteAll();
        bannerRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 → 200 + ACTIVE/HIDDEN 포함, DELETED 제외")
    void getBanners_returns_200_excludes_deleted() throws Exception {
        String adminToken = adminToken();
        saveBanner("배너A", 0, BannerStatus.ACTIVE);
        saveBanner("배너B", 1, BannerStatus.HIDDEN);
        saveBanner("배너C", 2, BannerStatus.DELETED);

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("GET 목록 status 필터 → 해당 상태만")
    void getBanners_status_filter() throws Exception {
        String adminToken = adminToken();
        saveBanner("노출중", 0, BannerStatus.ACTIVE);
        saveBanner("숨김", 1, BannerStatus.HIDDEN);

        mockMvc.perform(get("/api/v1/admin/banners")
                        .param("status", "HIDDEN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"));
    }

    @Test
    @DisplayName("GET 목록 cursor 페이징 → priority ASC 정렬 + size+1 sentinel로 hasNext/nextCursor")
    void getBanners_cursor_paging() throws Exception {
        String adminToken = adminToken();
        saveBanner("우선0", 0, BannerStatus.ACTIVE);
        saveBanner("우선1", 1, BannerStatus.ACTIVE);
        saveBanner("우선2", 2, BannerStatus.ACTIVE);

        MvcResult first = mockMvc.perform(get("/api/v1/admin/banners")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                // priority ASC 정렬 — 첫 페이지는 priority 0,1
                .andExpect(jsonPath("$.data.content[0].priority").value(0))
                .andExpect(jsonPath("$.data.content[1].priority").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(first.getResponse().getContentAsString());
        String nextCursor = body.get("data").get("nextCursor").asString();

        mockMvc.perform(get("/api/v1/admin/banners")
                        .param("size", "2")
                        .param("cursor", nextCursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].priority").value(2));
    }

    // ── 등록 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 등록 → 201 + Banner ACTIVE 저장 + audit BANNER_CREATE(targetId=생성 id, returnIdField)")
    void createBanner_returns_201_and_audit_targetId_from_return() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"title":"신년 배너","imageUrl":"https://cdn/ny.png","linkUrl":"https://event/ny",
                 "priority":3,"startDate":"2026-01-01","endDate":"2026-01-31"}
                """;

        mockMvc.perform(post("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("신년 배너"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.priority").value(3));

        assertThat(bannerRepository.findAll()).hasSize(1);
        Long createdId = bannerRepository.findAll().getFirst().getId();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.BANNER_CREATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.BANNER);
        assertThat(log.getTargetId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("POST 등록 필수 누락(title 빈값) → 400")
    void createBanner_blank_title_returns_400() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"title":"","imageUrl":"https://cdn/x.png"}
                """;

        mockMvc.perform(post("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST 노출 기간 역전(startDate > endDate) → 400")
    void createBanner_period_inversion_returns_400() throws Exception {
        String adminToken = adminToken();
        String json = """
                {"title":"역전 배너","imageUrl":"https://cdn/x.png",
                 "startDate":"2026-02-01","endDate":"2026-01-01"}
                """;

        mockMvc.perform(post("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    // ── 수정 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH 수정 → 200 + partial(미지정 필드 보존) + audit BANNER_UPDATE(targetId=bannerId)")
    void updateBanner_partial_returns_200_and_audit() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("기존 배너", 5, BannerStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value(1))
                // 미지정 필드 보존
                .andExpect(jsonPath("$.data.title").value("기존 배너"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn/img.png"));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.BANNER_UPDATE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.BANNER);
        assertThat(log.getTargetId()).isEqualTo(banner.getId());
    }

    @Test
    @DisplayName("PATCH 빈 body({}) → 400 + audit 미기록")
    void updateBanner_empty_body_returns_400() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("기존 배너", 0, BannerStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 공백-only 필드 → 400")
    void updateBanner_blank_field_returns_400() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("기존 배너", 0, BannerStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH 미존재 bannerId → 404 BANNER_001 + audit 미기록")
    void updateBanner_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/admin/banners/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_001"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 이미 DELETED → 409 BANNER_002 + audit 미기록 (멱등 가드)")
    void updateBanner_alreadyDeleted_returns_409() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("삭제됨", 0, BannerStatus.DELETED);

        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_002"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 한쪽 날짜만 수정해 기존 값과 역전 → 400 + audit 미기록 (리뷰 R1 회귀)")
    void updateBanner_periodInversion_returns_400() throws Exception {
        String adminToken = adminToken();
        // 기존 start=7/1, end=8/31
        Banner banner = bannerRepository.save(Banner.builder()
                .title("기간 배너")
                .imageUrl("https://cdn/img.png")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .build());

        // endDate만 6/1로 수정 → 기존 startDate(7/1)보다 빨라 역전 → 도메인 병합 재검증 400
        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest());

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    // ── 삭제 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE → 204 + status DELETED + 목록 제외 + audit BANNER_DELETE")
    void deleteBanner_returns_204_soft_delete_and_audit() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("삭제할 배너", 0, BannerStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Banner reloaded = bannerRepository.findById(banner.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BannerStatus.DELETED);

        // 목록에서 제외
        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.BANNER_DELETE);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(banner.getId());
    }

    @Test
    @DisplayName("DELETE 미존재 bannerId → 404 BANNER_001 + audit 미기록")
    void deleteBanner_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(delete("/api/v1/admin/banners/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_001"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("DELETE 이미 DELETED → 409 BANNER_002 + audit 미기록 (멱등 가드)")
    void deleteBanner_alreadyDeleted_returns_409() throws Exception {
        String adminToken = adminToken();
        Banner banner = saveBanner("삭제됨", 0, BannerStatus.DELETED);

        mockMvc.perform(delete("/api/v1/admin/banners/" + banner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_002"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    // ── cursor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 잘못된 cursor → 400 COMMON_008")
    void getBanners_invalidCursor_returns_400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("cursor", "!!!not-base64!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    @DisplayName("GET 목록 status=DELETED 필터 → 400 COMMON_004 (미지원 입력, 리뷰 #3)")
    void getBanners_statusDeleted_returns_400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    // ── 접근 제어 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user@test.com", PASSWORD, "유저",
                com.chunbaetour.domain.auth.Role.USER, com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchant_token_forbidden() throws Exception {
        seedFactory.seedMerchant("merchant@test.com", PASSWORD, "상인");
        String merchantToken = loginAndGetToken("/api/v1/merchants/auth/login", "merchant@test.com");

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Banner saveBanner(String title, int priority, BannerStatus status) {
        Banner banner = Banner.builder()
                .title(title)
                .imageUrl("https://cdn/img.png")
                .linkUrl("https://event/x")
                .priority(priority)
                .build();
        if (status == BannerStatus.HIDDEN) {
            banner.hide();
        } else if (status == BannerStatus.DELETED) {
            banner.delete();
        }
        return bannerRepository.save(banner);
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
