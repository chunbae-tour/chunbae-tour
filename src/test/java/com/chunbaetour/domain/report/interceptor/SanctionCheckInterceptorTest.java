package com.chunbaetour.domain.report.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link SanctionCheckInterceptor} 단위 테스트 (PR4).
 * 계정 레벨 정지 차단 + 도메인별 쓰기 차단 + GET·admin·비인증 우회.
 */
class SanctionCheckInterceptorTest {

    private static final Long USER_ID = 7L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final UserSanctionRepository userSanctionRepository = mock(UserSanctionRepository.class);
    private final SanctionCheckInterceptor interceptor =
            new SanctionCheckInterceptor(accountRepository, userSanctionRepository, clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(USER_ID, null));
    }

    private HttpServletRequest request(String method, String path) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        given(req.getMethod()).willReturn(method);
        given(req.getServletPath()).willReturn(path);
        return req;
    }

    @Test
    @DisplayName("GET 요청은 항상 통과")
    void get_always_passes() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        given(req.getMethod()).willReturn("GET");

        assertThat(interceptor.preHandle(req, null, null)).isTrue();
    }

    @Test
    @DisplayName("비인증 요청은 통과 (Security가 처리)")
    void unauthenticated_passes() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        given(req.getMethod()).willReturn("POST");

        assertThat(interceptor.preHandle(req, null, null)).isTrue();
    }

    @Test
    @DisplayName("관리자 경로는 제재 적용 제외")
    void admin_path_passes() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/admin/reports/1/resolve");

        assertThat(interceptor.preHandle(req, null, null)).isTrue();
    }

    @Test
    @DisplayName("계정 SUSPENDED + 활성 제재 → ACCOUNT_SUSPENDED")
    void account_suspended_throws() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/community/posts/free");
        Account account = mock(Account.class);
        given(account.getStatus()).willReturn(AccountStatus.SUSPENDED);
        given(account.isCurrentlySanctioned(any())).willReturn(true);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        assertThatThrownBy(() -> interceptor.preHandle(req, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("도메인(POST_FREE) 활성 제재 → DOMAIN_SANCTIONED")
    void domain_sanction_throws() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/community/posts/free");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(userSanctionRepository.findActiveSanctionsByUserIdAndTargetType(
                eq(USER_ID), eq(ReportTargetType.POST_FREE), any()))
                .willReturn(List.of(mock(UserSanction.class)));

        assertThatThrownBy(() -> interceptor.preHandle(req, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DOMAIN_SANCTIONED);
    }

    @Test
    @DisplayName("도메인 제재 없으면 통과")
    void domain_no_sanction_passes() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/community/posts/free");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(userSanctionRepository.findActiveSanctionsByUserIdAndTargetType(
                eq(USER_ID), eq(ReportTargetType.POST_FREE), any()))
                .willReturn(List.of());

        assertThat(interceptor.preHandle(req, null, null)).isTrue();
    }

    @Test
    @DisplayName("리뷰 쓰기 경로 → REVIEW 도메인 매핑, 활성 제재 시 DOMAIN_SANCTIONED")
    void review_path_maps_to_review_domain() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/places/5/reviews");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(userSanctionRepository.findActiveSanctionsByUserIdAndTargetType(
                eq(USER_ID), eq(ReportTargetType.REVIEW), any()))
                .willReturn(List.of(mock(UserSanction.class)));

        assertThatThrownBy(() -> interceptor.preHandle(req, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DOMAIN_SANCTIONED);
    }

    @Test
    @DisplayName("매핑 안 된 경로는 통과")
    void unmapped_path_passes() {
        authenticate();
        HttpServletRequest req = request("POST", "/api/v1/payments/charge");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThat(interceptor.preHandle(req, null, null)).isTrue();
    }
}
