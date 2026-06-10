package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth 토큰 교환 에러 분류기 (KAN 소셜로그인) — 400/502 매핑은 "정책"이라 클라이언트 구현에서 분리한다.
 *
 * <p><b>매핑 정책</b>: 인가코드 <i>자체</i> 문제(만료/재사용/무효 — 사용자가 다시 로그인하면 해소)만
 * 400({@code OAUTH_INVALID_AUTHORIZATION})으로 내린다. redirect_uri 불일치·키/시크릿 오류·필수 파라미터
 * 누락은 서버/설정 책임이므로 502({@code OAUTH_PROVIDER_ERROR})로 남겨, 설정 문제를 "사용자가 재시도하면
 * 되는 문제"처럼 숨기지 않는다(hyeonmin02·lim-haeun 리뷰 — 공급자 간 일관 정책).
 *
 * <ul>
 *   <li>KAKAO: {@code error_code=KOE320}(인가코드 만료/재사용)만 400. {@code invalid_grant} 광역 매칭은
 *       KOE303(redirect_uri 불일치)도 invalid_grant로 내려와 400으로 오분류하므로 쓰지 않는다.</li>
 *   <li>NAVER: {@code error=invalid_grant}(인가코드 만료/무효)만 400. {@code invalid_request}는
 *       redirect/파라미터 구성 오류를 포함하므로 제외.</li>
 * </ul>
 *
 * <p>공급자 추가(Google/Apple 등) 시 여기에 키워드만 늘린다 — 클라이언트는 분류를 모른다.
 */
final class OauthErrorClassifier {

    /** 로그 요약 최대 길이 — 과도한 본문(공급자 응답 변경/긴 description)으로 인한 로그 볼륨 폭주 방지. */
    private static final int MAX_LOG_LENGTH = 300;

    /** 토큰 에러 JSON에서 진단에 필요한 필드만 추출 — error / error_code / error_description. */
    private static final Pattern ERROR_FIELDS =
            Pattern.compile("\"(error|error_code|error_description)\"\\s*:\\s*\"([^\"]*)\"");

    private OauthErrorClassifier() {
    }

    /**
     * 토큰 교환 거부가 "인가코드 무효(사용자 재시도로 해소)"인지 판별.
     * true → 400 {@code OAUTH_INVALID_AUTHORIZATION}, false → 502 {@code OAUTH_PROVIDER_ERROR}.
     */
    static boolean isInvalidAuthorization(OauthProvider provider, String errorBody) {
        return switch (provider) {
            case KAKAO -> OauthResponses.containsAnyIgnoreCase(errorBody, "koe320");
            case NAVER -> OauthResponses.containsAnyIgnoreCase(errorBody, "invalid_grant");
        };
    }

    /**
     * 에러 본문을 로그 안전 형태로 요약 — {@code error}/{@code error_code}/{@code error_description}만
     * 추출하고 개행·제어문자 제거 + 길이 제한. 본문 원문 전체를 로깅하면 예기치 않은 민감 문자열,
     * 개행 기반 로그 오염, 로그 볼륨 문제가 생길 수 있다(hyeonmin02·CodeRabbit 리뷰).
     *
     * <p>JSON 파서 대신 정규식을 쓴다 — 추출 대상이 고정 3필드뿐이고, malformed 본문(HTML 에러 페이지 등)
     * 에도 예외 없이 동작해야 한다. 필드를 하나도 못 찾으면 새니타이즈한 앞부분만 남긴다.
     */
    static String summarizeForLog(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return "(empty)";
        }
        StringBuilder summary = new StringBuilder();
        Matcher matcher = ERROR_FIELDS.matcher(errorBody);
        while (matcher.find()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(matcher.group(1)).append('=').append(matcher.group(2));
        }
        String result = summary.length() > 0 ? summary.toString() : errorBody;
        result = result.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ");
        return result.length() > MAX_LOG_LENGTH
                ? result.substring(0, MAX_LOG_LENGTH) + "...(truncated)"
                : result;
    }
}
