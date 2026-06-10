package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.OauthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OauthErrorClassifier} 단위 테스트 — 공급자 에러 본문별 400/502 매핑 계약(hyeonmin02 리뷰 매트릭스)과
 * 로그 요약 새니타이즈. 판별이 "본문 contains"가 아닌 "추출 필드값 비교"임을 오분류 케이스로 고정한다.
 */
class OauthErrorClassifierTest {

    @Nested
    @DisplayName("카카오 — error_code=KOE320만 400, redirect/키 오류는 502")
    class Kakao {

        @Test
        @DisplayName("KOE320(인가코드 만료/재사용) → 400")
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

        @Test
        @DisplayName("error_description에 'KOE320' 문자열이 있어도 error_code가 아니면 502 — contains 오분류 방지")
        void koe320InDescriptionOnlyDoesNotMatch() {
            String body = "{\"error\":\"invalid_client\",\"error_code\":\"KOE101\","
                    + "\"error_description\":\"see docs about KOE320 for comparison\"}";
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.KAKAO, body)).isFalse();
        }
    }

    @Nested
    @DisplayName("네이버 — error=invalid_grant만 400, invalid_request/invalid_client는 502")
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
        @DisplayName("error_description에 'invalid_grant'가 있어도 error 필드가 아니면 502 — contains 오분류 방지")
        void invalidGrantInDescriptionOnlyDoesNotMatch() {
            String body = "{\"error\":\"invalid_request\","
                    + "\"error_description\":\"this is not an invalid_grant case\"}";
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER, body)).isFalse();
        }

        @Test
        @DisplayName("malformed/빈 본문 → 502")
        void malformedBodyStaysProviderError() {
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER, "<html>502</html>")).isFalse();
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.NAVER, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("로그 요약 — 추출 필드만 로깅, 미추출 시 본문 일절 미노출")
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
        @DisplayName("필드 미추출(HTML/WAF/스키마 변경) → 본문 미노출, 고정 문구+길이만")
        void unparsableBodyIsNeverLogged() {
            String html = "<html><body>Bad Gateway secret-trace-xyz</body></html>";

            String summary = OauthErrorClassifier.summarizeForLog(html);

            assertThat(summary).isEqualTo("unparsable_error_body(length=" + html.length() + ")");
            assertThat(summary).doesNotContain("secret-trace-xyz").doesNotContain("Bad Gateway");
        }

        @Test
        @DisplayName("개행/제어문자 제거 — 로그 오염(개행 기반 위조 라인) 방지")
        void stripsControlChars() {
            String withRealNewline = "{\"error\":\"x\",\"error_description\":\"line1\nFAKE\"}";
            assertThat(OauthErrorClassifier.summarizeForLog(withRealNewline)).doesNotContain("\n");
        }

        @Test
        @DisplayName("긴 description은 300자 truncate")
        void truncatesLongSummary() {
            String summary = OauthErrorClassifier.summarizeForLog(
                    "{\"error_description\":\"" + "x".repeat(1000) + "\"}");

            assertThat(summary.length()).isLessThanOrEqualTo(300 + "...(truncated)".length());
            assertThat(summary).endsWith("...(truncated)");
        }

        @Test
        @DisplayName("null/빈 본문 → (empty)")
        void nullOrBlank() {
            assertThat(OauthErrorClassifier.summarizeForLog(null)).isEqualTo("(empty)");
            assertThat(OauthErrorClassifier.summarizeForLog("  ")).isEqualTo("(empty)");
        }

        @Test
        @DisplayName("description에 이스케이프 따옴표(\\\") → 부분 추출(과소 노출, 안전 방향) + 판별 무영향")
        void escapedQuoteInDescriptionDegradesGracefully() {
            // value 중간의 \" 에서 매치가 끊겨 description은 부분만 남는다 — 진단용이라 허용.
            String body = "{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\","
                    + "\"error_description\":\"code \\\"abc\\\" not found\"}";

            String summary = OauthErrorClassifier.summarizeForLog(body);
            assertThat(summary).startsWith("error=invalid_grant, error_code=KOE320");
            // 판별 필드는 온전히 추출돼 분류는 정상 동작.
            assertThat(OauthErrorClassifier.isInvalidAuthorization(OauthProvider.KAKAO, body)).isTrue();
        }
    }
}
