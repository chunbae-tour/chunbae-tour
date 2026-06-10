package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.OauthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OauthErrorClassifier} 단위 테스트 — 공급자 에러 본문별 400/502 매핑 계약(hyeonmin02 리뷰 매트릭스)과
 * 로그 요약 새니타이즈. contains() 조건 변경으로 응답 계약이 깨지는 회귀를 방지한다.
 */
class OauthErrorClassifierTest {

    @Nested
    @DisplayName("카카오 — 인가코드 자체 문제(KOE320)만 400, redirect/키 오류는 502")
    class Kakao {

        @Test
        @DisplayName("KOE320(인가코드 만료/재사용) → 400 (invalid_grant 동반이어도 코드 기준)")
        void koe320IsInvalidAuthorization() {
            String body = "{\"error\":\"invalid_grant\",\"error_description\":\"authorization code not found\","
                    + "\"error_code\":\"KOE320\"}";
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.KAKAO, body)).isTrue();
        }

        @Test
        @DisplayName("KOE303(redirect_uri 불일치, error=invalid_grant로 옴) → 502 — 설정 오류는 서버 책임 정책")
        void koe303RedirectMismatchStaysProviderError() {
            String body = "{\"error\":\"invalid_grant\",\"error_description\":\"Redirect URI mismatch.\","
                    + "\"error_code\":\"KOE303\"}";
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.KAKAO, body)).isFalse();
        }

        @Test
        @DisplayName("KOE101(앱키 오류)/invalid_client → 502")
        void appKeyErrorsStayProviderError() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.KAKAO,
                    "{\"error\":\"invalid_client\",\"error_code\":\"KOE101\"}")).isFalse();
        }
    }

    @Nested
    @DisplayName("네이버 — invalid_grant만 400, invalid_request/invalid_client는 502")
    class Naver {

        @Test
        @DisplayName("invalid_grant(인가코드 만료/무효) → 400")
        void invalidGrantIsInvalidAuthorization() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER,
                    "{\"error\":\"invalid_grant\",\"error_description\":\"...\"}")).isTrue();
        }

        @Test
        @DisplayName("invalid_client(키 오류) → 502")
        void invalidClientStaysProviderError() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER,
                    "{\"error\":\"invalid_client\"}")).isFalse();
        }

        @Test
        @DisplayName("invalid_request(redirect/파라미터 구성 오류 포함) → 502 — 카카오 redirect 정책과 일관")
        void invalidRequestStaysProviderError() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER,
                    "{\"error\":\"invalid_request\",\"error_description\":\"no valid data in session\"}")).isFalse();
        }

        @Test
        @DisplayName("malformed/빈 본문 → 502")
        void malformedBodyStaysProviderError() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER, "<html>502</html>")).isFalse();
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("로그 요약 — 진단 필드만 추출 + 새니타이즈 + 길이 제한 (raw 본문 미로깅)")
    class SummarizeForLog {

        @Test
        @DisplayName("error/error_code/error_description만 추출 — 그 외 필드는 버린다")
        void extractsOnlyDiagnosticFields() {
            String body = "{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\","
                    + "\"error_description\":\"authorization code not found\",\"trace_id\":\"sensitive-123\"}";

            String summary = OauthErrorClassifier.summarizeForLog(body);

            assertThat(summary).isEqualTo(
                    "error=invalid_grant, error_code=KOE320, error_description=authorization code not found");
            assertThat(summary).doesNotContain("sensitive-123");
        }

        @Test
        @DisplayName("개행/제어문자 제거 — 로그 오염(개행 기반 위조 라인) 방지")
        void stripsControlChars() {
            String body = "{\"error\":\"x\",\"error_description\":\"line1\\nFAKE-LOG-LINE\"}";
            // JSON 문자열 내 이스케이프가 아닌 실제 개행이 섞인 본문도 안전해야 한다.
            String withRealNewline = "{\"error\":\"x\",\"error_description\":\"line1\nFAKE\"}";

            assertThat(OauthErrorClassifier.summarizeForLog(withRealNewline)).doesNotContain("\n");
            assertThat(OauthErrorClassifier.summarizeForLog(body)).doesNotContain("\n");
        }

        @Test
        @DisplayName("긴 본문은 300자 truncate")
        void truncatesLongBody() {
            String longDescription = "x".repeat(1000);
            String summary = OauthErrorClassifier.summarizeForLog(
                    "{\"error_description\":\"" + longDescription + "\"}");

            assertThat(summary.length()).isLessThanOrEqualTo(300 + "...(truncated)".length());
            assertThat(summary).endsWith("...(truncated)");
        }

        @Test
        @DisplayName("파싱 불가 본문(HTML 등) → 새니타이즈된 앞부분만, null/빈 → (empty)")
        void unparsableFallsBackSanitized() {
            assertThat(OauthErrorClassifier.summarizeForLog("<html>\nBad Gateway</html>"))
                    .doesNotContain("\n")
                    .contains("Bad Gateway");
            assertThat(OauthErrorClassifier.summarizeForLog(null)).isEqualTo("(empty)");
            assertThat(OauthErrorClassifier.summarizeForLog("  ")).isEqualTo("(empty)");
        }
    }
}
