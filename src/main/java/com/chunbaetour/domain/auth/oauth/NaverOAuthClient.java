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
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 로그인 OAuth 클라이언트.
 *
 * <p>토큰: {@code POST https://nid.naver.com/oauth2.0/token} (form-urlencoded).
 * 사용자: {@code GET https://openapi.naver.com/v1/nid/me} (Bearer) — {@code response} 객체 안에 id/email/nickname.
 *
 * <p>키({@code oauth.naver.client-id}, {@code oauth.naver.client-secret})는 네이버 Developers 앱에서 발급.
 */
@Slf4j
@Component
public class NaverOAuthClient implements OauthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient oauthRestClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String userInfoUrl;

    public NaverOAuthClient(
            RestClient oauthRestClient,
            @Value("${oauth.naver.client-id:}") String clientId,
            @Value("${oauth.naver.client-secret:}") String clientSecret,
            @Value("${oauth.naver.token-url:https://nid.naver.com/oauth2.0/token}") String tokenUrl,
            @Value("${oauth.naver.user-info-url:https://openapi.naver.com/v1/nid/me}") String userInfoUrl
    ) {
        this.oauthRestClient = oauthRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.NAVER;
    }

    @Override
    public OauthUserInfo fetch(String code, String redirectUri) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.error("[OAuth/Naver] client-id/secret 미설정 — oauth.naver.* 환경변수를 확인하세요.");
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        String accessToken = requestAccessToken(code, redirectUri);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            Map<String, Object> body = oauthRestClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            String token = body == null ? null : OauthResponses.asString(body.get("access_token"));
            if (token == null) {
                log.warn("[OAuth/Naver] 토큰 응답에 access_token 없음");
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return token;
        } catch (RestClientResponseException e) {
            // 네이버가 4xx/5xx로 거부 — 에러 본문(error/error_description)은 비밀이 아니며 원인 진단에 필수다
            // (예: invalid_request=파라미터/redirect 문제, invalid_client=키 오류). 요청 비밀값은 echo되지 않음.
            String errorBody = e.getResponseBodyAsString();
            log.warn("[OAuth/Naver] 토큰 교환 거부: status={}, body={}", e.getStatusCode(), errorBody);
            // 인가코드 만료/무효(invalid_grant)만 4xx로. invalid_request는 redirect_uri 불일치·필수 파라미터
            // 누락 등 "설정/요청 자체 오류"를 포함하므로(카카오의 redirect 불일치가 502로 남는 것과 일관되게)
            // 매칭에서 제외 → 502 유지(lim-haeun 리뷰: 공급자 간 redirect 불일치 응답코드 일관성).
            if (OauthResponses.containsAnyIgnoreCase(errorBody, "invalid_grant")) {
                throw new BusinessException(ErrorCode.OAUTH_INVALID_AUTHORIZATION);
            }
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        } catch (RestClientException e) {
            log.warn("[OAuth/Naver] 토큰 교환 네트워크 오류: {}", e.getClass().getSimpleName());
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
            Map<String, Object> response = body == null ? null : OauthResponses.asMap(body.get("response"));
            if (response == null) {
                log.warn("[OAuth/Naver] 사용자 정보 response 객체 없음");
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            String oauthId = OauthResponses.asString(response.get("id"));
            if (oauthId == null) {
                log.warn("[OAuth/Naver] 사용자 정보에 id 없음");
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            String email = OauthResponses.asString(response.get("email"));
            String nickname = OauthResponses.asString(response.get("nickname"));
            return new OauthUserInfo(OauthProvider.NAVER, oauthId, email, nickname);
        } catch (RestClientException e) {
            log.warn("[OAuth/Naver] 사용자 정보 조회 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }
}
