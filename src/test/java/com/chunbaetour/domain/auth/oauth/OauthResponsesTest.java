package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OauthResponses#containsAnyIgnoreCase} 단위 테스트 — 문자열 판별 유틸 자체의 계약(매칭/경계값).
 * 공급자별 400/502 매핑 정책은 {@link OauthErrorClassifierTest}가 검증한다.
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

    @DisplayName("키워드 경계값 — null 배열은 NPE 없이 false, null/blank 키워드는 매치되지 않음")
    @Test
    void nullOrBlankKeywords() {
        assertThat(OauthResponses.containsAnyIgnoreCase("{\"error\":\"x\"}", (String[]) null)).isFalse();
        // blank 키워드가 모든 본문과 매치돼 전부 400으로 오분류되는 것 방지.
        assertThat(OauthResponses.containsAnyIgnoreCase("{\"error\":\"x\"}", "", "  ")).isFalse();
        assertThat(OauthResponses.containsAnyIgnoreCase("{\"error\":\"x\"}", (String) null)).isFalse();
        // 유효 키워드가 섞여 있으면 그 키워드로는 정상 매치.
        assertThat(OauthResponses.containsAnyIgnoreCase("{\"error\":\"invalid_grant\"}", "", "invalid_grant")).isTrue();
    }
}
