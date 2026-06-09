package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 OAuth 클라이언트.
 *
 * <p>토큰: {@code POST https://kauth.kakao.com/oauth/token} (form-urlencoded).
 * 사용자: {@code GET https://kapi.kakao.com/v2/user/me} (Bearer).
 *
 * <p>키({@code oauth.kakao.client-id})는 카카오 Developers 앱의 REST API 키이며, 해당 앱에 "카카오 로그인"
 * 제품이 활성화되고 redirect URI가 등록돼 있어야 한다. client-secret은 앱 설정에서 사용하도록 켠 경우에만 전송.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OauthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient oauthRestClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String userInfoUrl;

    public KakaoOAuthClient(
            RestClient oauthRestClient,
            @Value("${oauth.kakao.client-id:}") String clientId,
            @Value("${oauth.kakao.client-secret:}") String clientSecret,
            @Value("${oauth.kakao.token-url:https://kauth.kakao.com/oauth/token}") String tokenUrl,
            @Value("${oauth.kakao.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl
    ) {
        this.oauthRestClient = oauthRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.KAKAO;
    }

    @Override
    public OauthUserInfo fetch(String code, String redirectUri) {
        if (clientId == null || clientId.isBlank()) {
            // 키 미설정 — 부팅은 막지 않되(다른 환경 영향 차단) 요청 시점에 명확히 거부.
            log.error("[OAuth/Kakao] client-id 미설정 — oauth.kakao.client-id 환경변수를 확인하세요.");
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        String accessToken = requestAccessToken(code, redirectUri);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        try {
            Map<String, Object> body = oauthRestClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            String token = body == null ? null : OauthResponses.asString(body.get("access_token"));
            if (token == null) {
                log.warn("[OAuth/Kakao] 토큰 응답에 access_token 없음");
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return token;
        } catch (RestClientException e) {
            // 응답 바디(코드/토큰 등 민감값)는 로그에 남기지 않는다.
            log.warn("[OAuth/Kakao] 토큰 교환 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private OauthUserInfo requestUserInfo(String accessToken) {
        try {
            Map<String, Object> body = oauthRestClient.get()
                    .uri(userInfoUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(MAP_TYPE);
            if (body == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            String oauthId = OauthResponses.asString(body.get("id"));
            if (oauthId == null) {
                log.warn("[OAuth/Kakao] 사용자 정보에 id 없음");
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            Map<String, Object> account = OauthResponses.asMap(body.get("kakao_account"));
            String email = account == null ? null : OauthResponses.asString(account.get("email"));
            Map<String, Object> profile = account == null ? null : OauthResponses.asMap(account.get("profile"));
            String nickname = profile == null ? null : OauthResponses.asString(profile.get("nickname"));
            return new OauthUserInfo(OauthProvider.KAKAO, oauthId, email, nickname);
        } catch (RestClientException e) {
            log.warn("[OAuth/Kakao] 사용자 정보 조회 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }
}
