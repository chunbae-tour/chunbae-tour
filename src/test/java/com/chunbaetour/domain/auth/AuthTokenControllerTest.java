package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.dto.ReissueResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.CookieProperties;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link AuthTokenController} 단위 테스트.
 *
 * <p>Refresh Cookie 이름은 운영 설정으로 바뀔 수 있으므로, 발급과 조회가 같은 {@link CookieProperties}를
 * 참조하는지 컨트롤러 레벨에서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenControllerTest {

    @Mock
    private ReissueService reissueService;

    @Mock
    private RefreshCookieFactory refreshCookieFactory;

    @Test
    void reissue_reads_refresh_cookie_using_configured_name() {
        CookieProperties cookieProperties = new CookieProperties(
                "customRefreshToken", false, "Lax", "/api/v1/auth");
        AuthTokenController controller =
                new AuthTokenController(reissueService, refreshCookieFactory, cookieProperties);
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
        CookieProperties cookieProperties = new CookieProperties(
                "customRefreshToken", false, "Lax", "/api/v1/auth");
        AuthTokenController controller =
                new AuthTokenController(reissueService, refreshCookieFactory, cookieProperties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "old-hardcoded-name"));

        assertThatThrownBy(() -> controller.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }
}
