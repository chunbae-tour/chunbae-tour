package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OauthResponses#containsAnyIgnoreCase} 단위 테스트 — OAuth 토큰 교환 에러의 4xx/502 분기 근거.
 * 인가코드 무효(invalid_grant/KOE320)는 400, 키/설정 오류·네이버 invalid_request는 502로 남아야 한다.
 */
class OauthResponsesTest {

    @DisplayName("인가코드 무효(invalid_grant·KOE320, 대소문자 무시) → true (→ 400)")
    @Test
    void matchesInvalidGrant() {
        assertThat(OauthResponses.containsAnyIgnoreCase(
                "{\"error\":\"invalid_grant\",\"error_description\":\"...\"}", "invalid_grant", "koe320")).isTrue();
        assertThat(OauthResponses.containsAnyIgnoreCase(
                "{\"error_code\":\"KOE320\"}", "invalid_grant", "koe320")).isTrue();
    }

    @DisplayName("키/설정 오류(KOE101·invalid_client) → false (→ 502, 서버 책임)")
    @Test
    void doesNotMatchConfigErrors() {
        assertThat(OauthResponses.containsAnyIgnoreCase(
                "{\"error_code\":\"KOE101\"}", "invalid_grant", "koe320")).isFalse();
        assertThat(OauthResponses.containsAnyIgnoreCase(
                "{\"error\":\"invalid_client\"}", "invalid_grant", "koe320")).isFalse();
    }

    @DisplayName("네이버 invalid_request(redirect/파라미터 오류) → false (→ 502, 카카오 redirect와 일관)")
    @Test
    void naverInvalidRequestStays502() {
        assertThat(OauthResponses.containsAnyIgnoreCase(
                "{\"error\":\"invalid_request\"}", "invalid_grant")).isFalse();
    }

    @DisplayName("null/빈 본문 → false")
    @Test
    void nullOrBlank() {
        assertThat(OauthResponses.containsAnyIgnoreCase(null, "invalid_grant")).isFalse();
        assertThat(OauthResponses.containsAnyIgnoreCase("", "invalid_grant")).isFalse();
        assertThat(OauthResponses.containsAnyIgnoreCase("   ", "invalid_grant")).isFalse();
    }
}
