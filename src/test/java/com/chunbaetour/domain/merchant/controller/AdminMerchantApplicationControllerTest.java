package com.chunbaetour.domain.merchant.controller;

import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.service.AdminMerchantApplicationService;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code AdminMerchantApplicationController.getApplication} 통합 테스트 (KAN-182, Epic KAN-177 S13).
 *
 * <p>검증 범위: 인증/인가 + GlobalExceptionHandler 응답 코드 매핑 + happy path 응답 필드.
 * 서비스는 {@link MockitoBean}으로 stub — 본 슬라이스 범위가 controller 신규 endpoint이라 service 구현은
 * 단위 테스트({@code AdminMerchantApplicationServiceTest})에서 별도 커버.
 *
 * <p>인증 토큰은 실제 {@link TokenIssuer}로 발급 → SecurityConfig의 {@code hasRole(ADMIN)} 매핑 + filter 분기를
 * 그대로 통과시켜 운영 흐름과 동일하게 검증 (PR #252 {@code TranslationControllerTest} 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMerchantApplicationControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @MockitoBean
    private AdminMerchantApplicationService adminMerchantApplicationService;

    private static final long APPLICATION_ID = 42L;
    private static final long ADMIN_USER_ID = 1L;
    private static final long USER_USER_ID = 2L;
    private static final long MERCHANT_USER_ID = 3L;
    private static final String URL = "/api/v1/admin/merchant-applications/" + APPLICATION_ID;

    private static MerchantApplicationDetailResponse approvedResponse() {
        return new MerchantApplicationDetailResponse(
                APPLICATION_ID,
                10L,
                "테스트가게",
                "1234567890",
                "한식",
                "서울시 강남구",
                MerchantApplicationStatus.APPROVED,
                null,
                LocalDateTime.of(2026, 5, 29, 12, 0, 0)
        );
    }

    private static MerchantApplicationDetailResponse rejectedResponse() {
        return new MerchantApplicationDetailResponse(
                APPLICATION_ID,
                10L,
                "테스트가게",
                "1234567890",
                "한식",
                "서울시 강남구",
                MerchantApplicationStatus.REJECTED,
                "사업자등록증 위조 의심",
                LocalDateTime.of(2026, 5, 29, 12, 0, 0)
        );
    }

    @Test
    @DisplayName("ADMIN 토큰 + 존재하는 ID → 200 + 응답 필드 검증")
    void getApplication_whenAdminAndExists_returns200() throws Exception {
        given(adminMerchantApplicationService.getApplication(APPLICATION_ID))
                .willReturn(approvedResponse());

        String token = tokenIssuer.issueAccess(ADMIN_USER_ID, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(APPLICATION_ID))
                .andExpect(jsonPath("$.data.shopName").value("테스트가게"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                // rejectReason은 null. MerchantApplicationDetailResponse(record)에 @JsonInclude 미부착 +
                // 글로벌 NON_NULL 정책 미설정이라 Jackson 기본 정책으로 키는 응답에 포함되며 값만 null.
                // 따라서 doesNotExist()가 아닌 value(null)로 검증해야 의도와 실제 응답이 일치.
                .andExpect(jsonPath("$.data.rejectReason").value(nullValue()));
    }

    @Test
    @DisplayName("ADMIN 토큰 + 거절된 신청 ID → 200 + rejectReason 포함")
    void getApplication_whenAdminAndRejected_includesRejectReason() throws Exception {
        // PRD AC: "거절된 신청 상세 응답 시 rejectReason 포함" — 본 케이스가 회귀 가드.
        given(adminMerchantApplicationService.getApplication(APPLICATION_ID))
                .willReturn(rejectedResponse());

        String token = tokenIssuer.issueAccess(ADMIN_USER_ID, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("사업자등록증 위조 의심"));
    }

    @Test
    @DisplayName("ADMIN 토큰 + 미존재 ID → 404 MERCHANT_APPLICATION_NOT_FOUND")
    void getApplication_whenAdminAndNotFound_returns404() throws Exception {
        given(adminMerchantApplicationService.getApplication(APPLICATION_ID))
                .willThrow(new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));

        String token = tokenIssuer.issueAccess(ADMIN_USER_ID, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                // ErrorCode 값 변경 회귀 가드 — 하드코딩 대신 enum 참조
                .andExpect(jsonPath("$.code").value(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007, service 미호출")
    void getApplication_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(USER_USER_ID, Role.USER, "user@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        verifyNoInteractions(adminMerchantApplicationService);
    }

    @Test
    @DisplayName("MERCHANT 토큰 → 403 AUTH_007, service 미호출")
    void getApplication_whenMerchant_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(MERCHANT_USER_ID, Role.MERCHANT, "merchant@test.com");

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        verifyNoInteractions(adminMerchantApplicationService);
    }

    @Test
    @DisplayName("미인증 → 401 AUTH_006, service 미호출")
    void getApplication_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));

        verifyNoInteractions(adminMerchantApplicationService);
    }

    @Test
    @DisplayName("음수 applicationId → 400, service 미호출 (@Positive 가드)")
    void getApplication_whenNegativeId_returns400() throws Exception {
        // PathVariable @Positive 위반 → ConstraintViolationException → GlobalExceptionHandler가 400 매핑.
        // service 단계 미진입 검증으로 입력 검증이 controller 경계에서 차단됨을 확인.
        String token = tokenIssuer.issueAccess(ADMIN_USER_ID, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get("/api/v1/admin/merchant-applications/-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminMerchantApplicationService);
    }
}
