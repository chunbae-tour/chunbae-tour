package com.chunbaetour.domain.admin.certification;

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
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.repository.ShopCertificationRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * 운영자 상인 인증 관리 API 통합 테스트 (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>목록(status 필터)/상세 200</li>
 *   <li>승인 200 → Shop.isCertified=true cascade + audit CERTIFICATION_APPROVE</li>
 *   <li>거절 200 → rejectReason 저장 + audit CERTIFICATION_REJECT</li>
 *   <li>취소 200 → cert.status=CANCELLED + cancelReason 저장 + isCertified=false 회복 + audit CERTIFICATION_CANCEL(targetId=certId)</li>
 *   <li>이미 APPROVED 재승인 409 / 미존재 certificationId 404 / 비APPROVED 취소 409 / 사유 누락 400</li>
 *   <li>USER/MERCHANT 토큰 403(AUTH_007)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminShopCertificationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopCertificationRepository certificationRepository;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private AccountRepository accountRepository;

    @AfterEach
    void cleanup() {
        adminActionLogRepository.deleteAll();
        certificationRepository.deleteAll();
        shopRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── 정상 흐름 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 목록 status 필터 → PENDING만")
    void list_status_filter_returns_pending() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification pending = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청 사유", LocalDateTime.now()));
        ShopCertification approved = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "다른 신청", LocalDateTime.now()));
        approved.approve(1L, LocalDateTime.now());
        certificationRepository.save(approved);

        mockMvc.perform(get("/api/v1/admin/shop-certifications")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(pending.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET 상세 → 200 + 신청 사유 포함")
    void detail_returns_200() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "사업자등록증 첨부", LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/admin/shop-certifications/" + cert.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(cert.getId()))
                .andExpect(jsonPath("$.data.shopId").value(shop.getId()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submittedReason").value("사업자등록증 첨부"));
    }

    @Test
    @DisplayName("PATCH 승인 → 200 + Shop.isCertified=true + audit CERTIFICATION_APPROVE")
    void approve_returns_200_and_marks_certified() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(shopRepository.findById(shop.getId()).orElseThrow().isCertified()).isTrue();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.CERTIFICATION_APPROVE);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.SHOP_CERTIFICATION);
        assertThat(log.getTargetId()).isEqualTo(cert.getId());
    }

    @Test
    @DisplayName("이미 인증된 가게의 다른 PENDING 신청 승인 → 409 SHOP_021 (가게당 APPROVED ≤ 1)")
    void approve_when_shop_already_certified_returns_409() throws Exception {
        // 불변식: 가게당 유효 인증 1건. 이미 인증된 가게에 남아있는 PENDING 신청을 승인하면
        // APPROVED가 2건이 되므로 SHOP_ALREADY_CERTIFIED(409)로 거부해야 한다.
        String adminToken = adminToken();
        Shop shop = seedShop();
        shop.markCertified();
        shopRepository.save(shop);
        ShopCertification secondPending = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "중복 신청", LocalDateTime.now()));

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + secondPending.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_021"));

        // 거부됐으므로 신청은 PENDING 그대로, audit 미기록(컨트롤러 AOP는 성공 시에만).
        assertThat(certificationRepository.findById(secondPending.getId()).orElseThrow().getStatus())
                .isEqualTo(com.chunbaetour.domain.shop.type.ShopCertificationStatus.PENDING);
        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PATCH 거절 → 200 + rejectReason 저장 + audit CERTIFICATION_REJECT")
    void reject_returns_200_and_records_reason() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"위생 기준 미달\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("위생 기준 미달"));

        ShopCertification reloaded = certificationRepository.findById(cert.getId()).orElseThrow();
        assertThat(reloaded.getRejectReason()).isEqualTo("위생 기준 미달");
        assertThat(shopRepository.findById(shop.getId()).orElseThrow().isCertified()).isFalse();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActionType()).isEqualTo(AdminActionType.CERTIFICATION_REJECT);
        assertThat(logs.getFirst().getTargetId()).isEqualTo(cert.getId());
    }

    @Test
    @DisplayName("PATCH 취소 → 200 + cert CANCELLED + cancelReason 저장 + isCertified=false + audit targetId=certId")
    void cancel_returns_200_and_unmarks_certified() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        // APPROVED 인증을 시드 (방향 B: 취소는 APPROVED cert에서만)
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));
        cert.approve(1L, LocalDateTime.now());
        certificationRepository.save(cert);
        shop.markCertified();
        shopRepository.save(shop);

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"인증 기준 미충족 발견\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("인증 기준 미충족 발견"))
                .andExpect(jsonPath("$.data.isCertified").value(false));

        ShopCertification reloaded = certificationRepository.findById(cert.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .isEqualTo(com.chunbaetour.domain.shop.type.ShopCertificationStatus.CANCELLED);
        assertThat(reloaded.getCancelReason()).isEqualTo("인증 기준 미충족 발견");
        assertThat(shopRepository.findById(shop.getId()).orElseThrow().isCertified()).isFalse();

        List<AdminActionLog> logs = adminActionLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AdminActionLog log = logs.getFirst();
        assertThat(log.getActionType()).isEqualTo(AdminActionType.CERTIFICATION_CANCEL);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.SHOP_CERTIFICATION);
        assertThat(log.getTargetId()).isEqualTo(cert.getId());
    }

    // ── 에러/접근 제어 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 APPROVED 재승인 → 409 SHOP_020 + audit 미기록")
    void reapprove_returns_409() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));
        cert.approve(1L, LocalDateTime.now());
        certificationRepository.save(cert);

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_020"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("미존재 certificationId 승인 → 404 SHOP_019 + audit 미기록")
    void approve_nonexistent_returns_404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/999999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_019"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("비APPROVED cert 취소 → 409 SHOP_020 + audit 미기록")
    void cancel_non_approved_returns_409() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        // PENDING 상태 cert는 취소 불가 (APPROVED만 허용)
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_020"));

        assertThat(adminActionLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("거절 사유 누락 → 400")
    void reject_blank_reason_returns_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("취소 사유 누락 → 400")
    void cancel_blank_reason_returns_400() throws Exception {
        String adminToken = adminToken();
        Shop shop = seedShop();
        ShopCertification cert = certificationRepository.save(
                ShopCertification.submit(shop.getId(), "신청", LocalDateTime.now()));
        cert.approve(1L, LocalDateTime.now());
        certificationRepository.save(cert);
        shop.markCertified();
        shopRepository.save(shop);

        mockMvc.perform(patch("/api/v1/admin/shop-certifications/" + cert.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seedAdmin("admin@test.com", PASSWORD, "관리자");
        seedFactory.seed("user@test.com", PASSWORD, "유저",
                com.chunbaetour.domain.auth.Role.USER, com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(get("/api/v1/admin/shop-certifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007")
    void merchant_token_forbidden() throws Exception {
        seedFactory.seedMerchant("merchant@test.com", PASSWORD, "상인");
        String merchantToken = loginAndGetToken("/api/v1/merchants/auth/login", "merchant@test.com");

        mockMvc.perform(get("/api/v1/admin/shop-certifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Shop seedShop() {
        return shopRepository.save(Shop.builder()
                .userId(1L)
                .applicationId(System.nanoTime())
                .shopName("테스트가게")
                .category("음식점")
                .address("춘천시")
                .lat(BigDecimal.valueOf(37.0))
                .lng(BigDecimal.valueOf(127.0))
                .description("설명")
                .build());
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
