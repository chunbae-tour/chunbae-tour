package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chunbaetour.domain.auth.dto.ReissueResponse;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.CookieProperties;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link AuthTokenController} 단위 테스트.
 *
 * <p>Refresh Cookie 이름은 운영 설정으로 바뀔 수 있으므로, 발급과 조회가 같은 {@link CookieProperties}를
 * 참조하는지 컨트롤러 레벨에서 고정한다.
 *
 * <p>S4 추가: logout endpoint가 request attribute의 AccessClaims를 LogoutService로 위임하고,
 * 만료 Cookie를 응답에 첨부하는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenControllerTest {

    private static final CookieProperties COOKIE_PROPS = new CookieProperties(
            "customRefreshToken", false, "Lax", "/api/v1/auth");

    @Mock
    private ReissueService reissueService;

    @Mock
    private LogoutService logoutService;

    @Mock
    private RefreshCookieFactory refreshCookieFactory;

    @Test
    void reissue_reads_refresh_cookie_using_configured_name() {
        AuthTokenController controller =
                new AuthTokenController(reissueService, logoutService, refreshCookieFactory, COOKIE_PROPS);
        TokenPair pair = new TokenPair("new-access", "new-refresh", "new-token-id", Role.USER);
        given(reissueService.reissue("current-refresh")).willReturn(pair);
        given(refreshCookieFactory.create("new-refresh"))
                .willReturn(ResponseCookie.from("customRefreshToken", "new-refresh").build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("refreshToken", "old-hardcoded-name"),
                new Cookie("customRefreshToken", "current-refresh"));

        ResponseEntity<ApiResponse<ReissueResponse>> response = controller.reissue(request);

        verify(reissueService).reissue("current-refresh");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("customRefreshToken=new-refresh");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().accessToken()).isEqualTo("new-access");
    }

    @Test
    void reissue_without_configured_cookie_returns_AUTH_005() {
        AuthTokenController controller =
                new AuthTokenController(reissueService, logoutService, refreshCookieFactory, COOKIE_PROPS);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "old-hardcoded-name"));

        assertThatThrownBy(() -> controller.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void logout_delegates_to_service_and_sets_expired_cookie() {
        // 정상 흐름: 인증 필터가 미리 request attribute에 AccessClaims를 넣어둠.
        AuthTokenController controller =
                new AuthTokenController(reissueService, logoutService, refreshCookieFactory, COOKIE_PROPS);
        AccessClaims claims = new AccessClaims(
                42L, Role.USER, "u@e.c", "tid", Instant.parse("2099-01-01T00:00:00Z"));
        given(refreshCookieFactory.expire())
                .willReturn(ResponseCookie.from("customRefreshToken", "").maxAge(0).build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS, claims);

        ResponseEntity<Void> response = controller.logout(request);

        // 1) LogoutService에 claims를 그대로 전달했는가
        verify(logoutService).logout(claims);
        // 2) 만료 Cookie를 Set-Cookie 헤더로 응답하는가
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("customRefreshToken=")
                .contains("Max-Age=0");
        // reissue 흐름은 호출 안 되어야
        verifyNoInteractions(reissueService);
    }

    @Test
    void logout_without_claims_attribute_throws_AUTH_006() {
        // 인증 필터가 정상이라면 attribute가 채워지는데, 비정상 상태 방어 코드 검증.
        AuthTokenController controller =
                new AuthTokenController(reissueService, logoutService, refreshCookieFactory, COOKIE_PROPS);

        MockHttpServletRequest request = new MockHttpServletRequest();
        // attribute 미설정 상태

        assertThatThrownBy(() -> controller.logout(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);

        // LogoutService 호출 안 됨 (검증 실패 시 부수 효과 없음)
        verifyNoInteractions(logoutService);
    }
}
