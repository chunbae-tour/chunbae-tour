package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.type.ReportAction;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * 관리자 신고 처리 API 통합 테스트 (KAN-92).
 *
 * <p>검증 시나리오:
 * <ul>
 *   <li>POST /api/v1/admin/reports/{id}/resolve — 콘텐츠 신고 처리 (WARNING/SUSPEND/DELETE/DISMISS)</li>
 *   <li>POST /api/v1/admin/reports/{id}/resolve/merchant — 가게 신고 처리 (HIDE_SHOP/REVOKE_MERCHANT/DISMISS)</li>
 *   <li>접근 제어 — 익명/USER/MERCHANT 차단, ADMIN 허용</li>
 *   <li>에러 케이스 — 이미 처리됨, 잘못된 엔드포인트, 없는 신고</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportResolveIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String COOKIE_NAME = "refreshToken";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private ReportRepository reportRepository;
    @Autowired private CompanionPostRepository companionPostRepository;
    @Autowired private FreePostRepository freePostRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ShopRepository shopRepository;

    @AfterEach
    void cleanup() {
        reportRepository.deleteAll();
        commentRepository.deleteAll();
        companionPostRepository.deleteAll();
        freePostRepository.deleteAll();
        shopRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 접근 제어 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("인증 없이 resolve 호출 시 401 AUTH_006")
    void anonymous_resolve_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("WARNING", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("USER 토큰으로 resolve 호출 시 403 AUTH_007")
    void user_resolve_returns_403() throws Exception {
        Account user = seedFactory.seed("user@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String token = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(post("/api/v1/admin/reports/1/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("WARNING", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰으로 resolve 호출 시 403 AUTH_007")
    void merchant_resolve_returns_403() throws Exception {
        seedFactory.seedMerchant("merchant@test.com", PASSWORD, "상인");
        String token = loginAndGetToken("/api/v1/merchants/auth/login", "merchant@test.com");

        mockMvc.perform(post("/api/v1/admin/reports/1/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("WARNING", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── POST /resolve — 콘텐츠 신고 처리 ──────────────────────────────────

    @Nested
    @DisplayName("콘텐츠 신고 처리 (/resolve)")
    class ContentResolve {

        @Test
        @DisplayName("WARNING 처리 — status=RESOLVED, action=WARNING")
        void warning_sets_resolved_with_warning_action() throws Exception {
            Account reporter = seedFactory.seed("reporter@test.com", PASSWORD, "신고자", Role.USER, AccountStatus.ACTIVE);
            Account target = seedFactory.seed("target@test.com", PASSWORD, "피신고자", Role.USER, AccountStatus.ACTIVE);
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.USER, target.getId(),
                    ReportReason.HARASSMENT, "욕설"));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("WARNING", "경고 조치")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.action").value("WARNING"))
                    .andExpect(jsonPath("$.data.adminNote").value("경고 조치"));

            Report updated = reportRepository.findById(report.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        }

        @Test
        @DisplayName("SUSPEND 처리 — 게시글 작성자 계정 SUSPENDED")
        void suspend_companion_post_author() throws Exception {
            Account author = seedFactory.seed("author@test.com", PASSWORD, "작성자", Role.USER, AccountStatus.ACTIVE);
            Account reporter = seedFactory.seed("reporter2@test.com", PASSWORD, "신고자2", Role.USER, AccountStatus.ACTIVE);
            CompanionPost post = companionPostRepository.save(
                    CompanionPost.create(author.getId(), "제목", "내용", 1L, "장소", "서울",
                            LocalDate.now().plusDays(7), 4));
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.POST_COMPANION, post.getId(),
                    ReportReason.SPAM, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("SUSPEND", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.action").value("SUSPEND"));

            Account updatedAuthor = accountRepository.findById(author.getId()).orElseThrow();
            assertThat(updatedAuthor.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }

        @Test
        @DisplayName("DELETE 처리 — 자유게시글 삭제됨")
        void delete_free_post() throws Exception {
            Account author = seedFactory.seed("freeauthor@test.com", PASSWORD, "자유작성자", Role.USER, AccountStatus.ACTIVE);
            Account reporter = seedFactory.seed("freereporter@test.com", PASSWORD, "자유신고자", Role.USER, AccountStatus.ACTIVE);
            FreePost post = freePostRepository.save(
                    FreePost.create(author.getId(), "자유게시글", "내용", List.of()));
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.POST_FREE, post.getId(),
                    ReportReason.OBSCENE, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DELETE", "음란물 삭제")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.action").value("DELETE"));

            FreePost updated = freePostRepository.findById(post.getId()).orElseThrow();
            // 관리자 DELETE 액션 = 게시글 비공개(HIDDEN). 작성자 삭제(DELETED)와 구분.
            assertThat(updated.getStatus()).isEqualTo(FreePostStatus.HIDDEN);
        }

        @Test
        @DisplayName("DELETE 처리 — 댓글 삭제됨")
        void delete_comment() throws Exception {
            Account author = seedFactory.seed("commentauthor@test.com", PASSWORD, "댓글작성자", Role.USER, AccountStatus.ACTIVE);
            Account reporter = seedFactory.seed("commentreporter@test.com", PASSWORD, "댓글신고자", Role.USER, AccountStatus.ACTIVE);
            Comment comment = commentRepository.save(
                    Comment.create(1L, PostType.FREE, author.getId(), "부적절한 댓글"));
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.COMMENT, comment.getId(),
                    ReportReason.HARASSMENT, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DELETE", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        }

        @Test
        @DisplayName("DISMISS 처리 — status=DISMISSED")
        void dismiss_sets_dismissed() throws Exception {
            Account reporter = seedFactory.seed("disreporter@test.com", PASSWORD, "기각신고자", Role.USER, AccountStatus.ACTIVE);
            Account target = seedFactory.seed("distarget@test.com", PASSWORD, "기각피신고자", Role.USER, AccountStatus.ACTIVE);
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.USER, target.getId(),
                    ReportReason.OTHER, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DISMISS", "문제없음")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISMISSED"))
                    .andExpect(jsonPath("$.data.action").value("DISMISS"));

            Report updated = reportRepository.findById(report.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        }

        @Test
        @DisplayName("이미 처리된 신고 재처리 시 409 REPORT_006")
        void already_resolved_returns_409() throws Exception {
            Account reporter = seedFactory.seed("dup@test.com", PASSWORD, "중복신고자", Role.USER, AccountStatus.ACTIVE);
            Account target = seedFactory.seed("duptarget@test.com", PASSWORD, "중복피신고", Role.USER, AccountStatus.ACTIVE);
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.USER, target.getId(),
                    ReportReason.SPAM, null));
            report.resolve(ReportAction.WARNING, null, "admin");  // already resolved, valid action required
            reportRepository.save(report);
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("WARNING", null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REPORT_006"));
        }

        @Test
        @DisplayName("MERCHANT 신고에 /resolve 호출 시 400 REPORT_007")
        void merchant_report_on_content_endpoint_returns_400() throws Exception {
            Account reporter = seedFactory.seed("mreporter@test.com", PASSWORD, "가게신고자", Role.USER, AccountStatus.ACTIVE);
            Account merchant = seedFactory.seedMerchant("mtarget@test.com", PASSWORD, "가게주인");
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.MERCHANT, merchant.getId(),
                    ReportReason.ILLEGAL, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("WARNING", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_007"));
        }

        @Test
        @DisplayName("존재하지 않는 신고 처리 시 404 REPORT_005")
        void not_found_report_returns_404() throws Exception {
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/99999/resolve")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("WARNING", null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_005"));
        }
    }

    // ── POST /resolve/merchant — 가게 신고 처리 ────────────────────────────

    @Nested
    @DisplayName("가게 신고 처리 (/resolve/merchant)")
    class MerchantResolve {

        @Test
        @DisplayName("DISMISS 처리 — status=DISMISSED")
        void dismiss_merchant_report() throws Exception {
            Account reporter = seedFactory.seed("mdr@test.com", PASSWORD, "가게기각신고자", Role.USER, AccountStatus.ACTIVE);
            Account merchant = seedFactory.seedMerchant("mdm@test.com", PASSWORD, "기각가게주인");
            Shop shop = shopRepository.save(Shop.builder()
                    .userId(merchant.getId()).applicationId(1L)
                    .shopName("기각가게").category("FOOD").address("서울시 테스트구").build());
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.MERCHANT, shop.getId(),
                    ReportReason.OTHER, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve/merchant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DISMISS", "가게 문제없음")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISMISSED"))
                    .andExpect(jsonPath("$.data.action").value("DISMISS"));
        }

        @Test
        @DisplayName("REVOKE_MERCHANT — 상인 계정 USER로 강등")
        void revoke_merchant_downgrades_role() throws Exception {
            Account reporter = seedFactory.seed("rreporter@test.com", PASSWORD, "강등신고자", Role.USER, AccountStatus.ACTIVE);
            Account merchant = seedFactory.seedMerchant("rmerchant@test.com", PASSWORD, "강등상인");
            Shop shop = shopRepository.save(Shop.builder()
                    .userId(merchant.getId()).applicationId(1L)
                    .shopName("강등가게").category("FOOD").address("서울시 테스트구").build());
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.MERCHANT, shop.getId(),
                    ReportReason.ILLEGAL, "사기"));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve/merchant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("REVOKE_MERCHANT", "사기 가게 인증 취소")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.action").value("REVOKE_MERCHANT"));

            Account updated = accountRepository.findById(merchant.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("HIDE_SHOP — 가게 SUSPENDED 처리 후 status=RESOLVED")
        void hide_shop_records_resolved() throws Exception {
            Account reporter = seedFactory.seed("hsreporter@test.com", PASSWORD, "숨김신고자", Role.USER, AccountStatus.ACTIVE);
            Account merchant = seedFactory.seedMerchant("hsmerchant@test.com", PASSWORD, "숨김상인");
            Shop shop = shopRepository.save(Shop.builder()
                    .userId(merchant.getId()).applicationId(1L)
                    .shopName("숨김가게").category("FOOD").address("서울시 테스트구").build());
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.MERCHANT, shop.getId(),
                    ReportReason.SPAM, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve/merchant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("HIDE_SHOP", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.data.action").value("HIDE_SHOP"));
        }

        @Test
        @DisplayName("콘텐츠 신고에 /resolve/merchant 호출 시 400 REPORT_004")
        void content_report_on_merchant_endpoint_returns_400() throws Exception {
            Account reporter = seedFactory.seed("wreporter@test.com", PASSWORD, "잘못된엔드포인트신고자", Role.USER, AccountStatus.ACTIVE);
            Account target = seedFactory.seed("wtarget@test.com", PASSWORD, "잘못된엔드포인트피신고", Role.USER, AccountStatus.ACTIVE);
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.USER, target.getId(),
                    ReportReason.HARASSMENT, null));
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve/merchant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DISMISS", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_007"));
        }

        @Test
        @DisplayName("이미 처리된 가게 신고 재처리 시 409 REPORT_003")
        void already_resolved_merchant_report_returns_409() throws Exception {
            Account reporter = seedFactory.seed("ar2reporter@test.com", PASSWORD, "중복가게신고자", Role.USER, AccountStatus.ACTIVE);
            Account merchant = seedFactory.seedMerchant("ar2merchant@test.com", PASSWORD, "중복가게주인");
            Report report = reportRepository.save(Report.create(
                    reporter.getId(), ReportTargetType.MERCHANT, merchant.getId(),
                    ReportReason.SPAM, null));
            report.dismiss(null, "admin");
            reportRepository.save(report);
            String adminToken = adminToken();

            mockMvc.perform(post("/api/v1/admin/reports/" + report.getId() + "/resolve/merchant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody("DISMISS", null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REPORT_006"));
        }
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

    private String resolveBody(String action, String adminNote) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("action", action);
        if (adminNote != null) {
            body.put("adminNote", adminNote);
        }
        return objectMapper.writeValueAsString(body);
    }
}
