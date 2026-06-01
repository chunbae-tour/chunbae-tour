package com.chunbaetour.domain.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
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
 * 운영자 사용자 관리 API 통합 테스트 (KAN-180, Admin Epic KAN-177 S02).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>목록/상세/정지(201)/해제(204) 정상 흐름</li>
 *   <li>정지/해제 → {@code admin_action_logs} 자동 기록 + targetId=userId 정확 (S01 wiring 실동작)</li>
 *   <li>재정지 갱신 (이미 SUSPENDED 계정 재호출 → 201, 사유/기간 덮어쓰기)</li>
 *   <li>미존재 사용자 정지 → 404 + audit 미기록</li>
 *   <li>USER 토큰 403(AUTH_007) / 미인증 401(AUTH_006)</li>
 *   <li>정지된 사용자 로그인 → AUTH_012</li>
 * </ul>
 *
 * <p>통합 테스트는 {@code ddl-auto: create-drop}으로 entity 기반 schema 생성 — {@code suspended_reason}
 * 컬럼은 {@code Account @Column}에서, {@code admin_action_logs}는 {@code AdminActionLog} entity에서 자동 생성.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private ReportRepository reportRepository;

    @AfterEach
    void cleanup() {
        reportRepository.deleteAll();
        adminActionLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 정상 흐름 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 → 200 + 시드 사용자 포함")
    void getUsers_returns_200_with_content() throws Exception {
        String adminToken = adminToken();
        seedFactory.seed("u1@test.com", PASSWORD, "유저1", Role.USER, AccountStatus.ACTIVE);
        seedFactory.seed("u2@test.com", PASSWORD, "유저2", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3)); // admin + u1 + u2
    }

    @Test
    @DisplayName("GET 목록 status 필터 → SUSPENDED만")
    void getUsers_status_filter() throws Exception {
        String adminToken = adminToken();
        Account suspended = seedFactory.seed("s@test.com", PASSWORD, "정지자", Role.USER, AccountStatus.SUSPENDED);
        seedFactory.seed("a@test.com", PASSWORD, "활성자", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("status", "SUSPENDED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(suspended.getId()));
    }

    @Test
    @DisplayName("GET 상세 → 200 + 누적 신고 건수 포함")
    void getUser_detail_returns_200() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("target@test.com", PASSWORD, "상세대상", Role.USER, AccountStatus.ACTIVE);
        Account reporter = seedFactory.seed("reporter@test.com", PASSWORD, "신고자", Role.USER, AccountStatus.ACTIVE);
        reportRepository.save(Report.create(
                reporter.getId(), ReportTargetType.USER, target.getId(), ReportReason.HARASSMENT, null));

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(target.getId()))
                .andExpect(jsonPath("$.data.email").value("target@test.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reportCount").value(1));
    }

    @Test
    @DisplayName("POST 정지 → 201 + status SUSPENDED + audit 1건(targetId=userId)")
    void suspend_returns_201_and_records_audit() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("suspend@test.com", PASSWORD, "정지대상", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"욕설 신고 누적\",\"durationDays\":7}"))
                .andExpect(status().isCreated());

        Account reloaded = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.getSuspendedReason()).isEqualTo("욕설 신고 누적");
        assertThat(reloaded.getSuspendedUntil()).isNotNull();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.USER_SUSPEND);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(log.getTargetId()).isEqualTo(target.getId());
    }

    @Test
    @DisplayName("POST 정지 무기한(durationDays 미지정) → 201 + suspendedUntil null")
    void suspend_indefinite_returns_201() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("perma@test.com", PASSWORD, "무기한", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"반복 위반\"}"))
                .andExpect(status().isCreated());

        Account reloaded = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("재정지 → 201 + 사유/기간 갱신 (예외 없음)")
    void resuspend_updates_and_returns_201() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("re@test.com", PASSWORD, "재정지", Role.USER, AccountStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"연장 사유\",\"durationDays\":30}"))
                .andExpect(status().isCreated());

        Account reloaded = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.getSuspendedReason()).isEqualTo("연장 사유");
    }

    @Test
    @DisplayName("DELETE 해제 → 204 + status ACTIVE + audit USER_UNSUSPEND")
    void unsuspend_returns_204_and_records_audit() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("unsuspend@test.com", PASSWORD, "해제대상", Role.USER, AccountStatus.SUSPENDED);

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Account reloaded = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.getSuspendedReason()).isNull();
        assertThat(reloaded.getSuspendedUntil()).isNull();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.USER_UNSUSPEND);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(target.getId());
    }

    // ── 에러/접근 제어 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("미존재 사용자 정지 → 404 + audit 미기록")
    void suspend_nonexistent_returns_404_and_no_audit() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/admin/users/999999/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\",\"durationDays\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_015"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("미존재 사용자 해제 → 404 + audit 미기록")
    void unsuspend_nonexistent_returns_404_and_no_audit() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(delete("/api/v1/admin/users/999999/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_015"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("DELETED 계정 정지 → 400 AUTH_016 + audit 미기록")
    void suspend_deleted_account_returns_400_and_no_audit() throws Exception {
        // DELETED 시드는 deletedAt=null(builder 미세팅)이라 @SQLRestriction(deleted_at IS NULL)을
        // 통과해 findById로 조회됨 → status=DELETED 가드 도달 → BusinessException(USER_ALREADY_DELETED).
        String adminToken = adminToken();
        Account deleted = seedFactory.seed("deleted-sus@test.com", PASSWORD, "탈퇴정지", Role.USER, AccountStatus.DELETED);

        mockMvc.perform(post("/api/v1/admin/users/" + deleted.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\",\"durationDays\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_016"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("DELETED 계정 해제 → 400 AUTH_016 + audit 미기록")
    void unsuspend_deleted_account_returns_400_and_no_audit() throws Exception {
        String adminToken = adminToken();
        Account deleted = seedFactory.seed("deleted-uns@test.com", PASSWORD, "탈퇴해제", Role.USER, AccountStatus.DELETED);

        mockMvc.perform(delete("/api/v1/admin/users/" + deleted.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_016"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정지 사유 500자 초과 → 400")
    void suspend_reason_too_long_returns_400() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("toolong@test.com", PASSWORD, "장문사유", Role.USER, AccountStatus.ACTIVE);
        String over = "가".repeat(501);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + over + "\",\"durationDays\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정지 사유 누락 → 400")
    void suspend_blank_reason_returns_400() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("blank@test.com", PASSWORD, "사유없음", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"durationDays\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("미인증 → 401 AUTH_006")
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("정지된 사용자 로그인 → AUTH_012")
    void suspended_user_login_returns_AUTH_012() throws Exception {
        String adminToken = adminToken();
        Account target = seedFactory.seed("login@test.com", PASSWORD, "정지로그인", Role.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/suspensions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"정지\",\"durationDays\":1}"))
                .andExpect(status().isCreated());

        LoginRequest req = new LoginRequest("login@test.com", PASSWORD);
        mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_012"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

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
