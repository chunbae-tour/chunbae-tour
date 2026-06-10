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
 * <p>판별은 본문 substring 검색이 아니라 <b>추출한 필드값 비교</b>로 한다 — {@code error_description} 등
 * 다른 필드에 같은 문자열이 우연히 들어가도 오분류되지 않는다.
 * <ul>
 *   <li>KAKAO: {@code error_code == "KOE320"}(인가코드 만료/재사용)만 400. KOE303(redirect_uri 불일치)은
 *       {@code error=invalid_grant}로 내려오지만 설정 문제이므로 502.</li>
 *   <li>NAVER: {@code error == "invalid_grant"}(인가코드 만료/무효)만 400. {@code invalid_request}는
 *       redirect/파라미터 구성 오류를 포함하므로 502.</li>
 * </ul>
 *
 * <p>공급자 추가(Google/Apple 등) 시 여기에 비교만 늘린다 — 클라이언트는 분류를 모른다.
 */
final class OauthErrorClassifier {

    /** 로그 요약 최대 길이 — 과도한 본문(공급자 응답 변경/긴 description)으로 인한 로그 볼륨 폭주 방지. */
    private static final int MAX_LOG_LENGTH = 300;

    /**
     * 토큰 에러 JSON에서 진단/판별에 쓰는 필드만 추출 — error / error_code / error_description.
     * 한계: value 안의 이스케이프 따옴표(\")에서 매치가 끊겨 부분 추출될 수 있다 — 판별 대상인
     * error/error_code는 코드값(따옴표 미포함)이라 무영향, description은 로그 진단용 요약이라 허용
     * (과소 노출 방향이므로 보안상 안전).
     */
    private static final Pattern ERROR_FIELDS =
            Pattern.compile("\"(error|error_code|error_description)\"\\s*:\\s*\"([^\"]*)\"");

    private OauthErrorClassifier() {
    }

    /** 토큰 에러 본문에서 추출한 표준 필드. 미존재 필드는 null. */
    record ErrorFields(String error, String errorCode, String errorDescription) {
    }

    /**
     * 토큰 교환 거부가 "인가코드 무효(사용자 재시도로 해소)"인지 판별.
     * true → 400 {@code OAUTH_INVALID_AUTHORIZATION}, false → 502 {@code OAUTH_PROVIDER_ERROR}.
     */
    static boolean isInvalidAuthorization(OauthProvider provider, String errorBody) {
        ErrorFields fields = parse(errorBody);
        return switch (provider) {
            case KAKAO -> "KOE320".equalsIgnoreCase(fields.errorCode());
            case NAVER -> "invalid_grant".equalsIgnoreCase(fields.error());
        };
    }

    /**
     * 에러 본문을 로그 안전 형태로 요약 — 추출된 {@code error}/{@code error_code}/{@code error_description}만
     * 남기고 개행·제어문자 제거 + 길이 제한. 필드를 하나도 못 찾으면(HTML/WAF/프록시 오류 페이지, 스키마 변경 등)
     * <b>본문은 일절 남기지 않고</b> 고정 문구 + 길이만 기록한다 — 인증 플로우 로그에 외부 응답 원문이
     * 일부라도 적재되는 것을 차단(hyeonmin02 리뷰). 상세 원인이 필요하면 status + 길이로 재현 조사한다.
     */
    static String summarizeForLog(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return "(empty)";
        }
        ErrorFields fields = parse(errorBody);
        StringBuilder summary = new StringBuilder();
        appendField(summary, "error", fields.error());
        appendField(summary, "error_code", fields.errorCode());
        appendField(summary, "error_description", fields.errorDescription());
        if (summary.length() == 0) {
            return "unparsable_error_body(length=" + errorBody.length() + ")";
        }
        String result = summary.toString().replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ");
        return result.length() > MAX_LOG_LENGTH
                ? result.substring(0, MAX_LOG_LENGTH) + "...(truncated)"
                : result;
    }

    /** 본문에서 표준 에러 필드를 추출 — 키별 첫 매치만. JSON 파서 대신 정규식(고정 3필드 + malformed 강건). */
    private static ErrorFields parse(String errorBody) {
        String error = null;
        String errorCode = null;
        String errorDescription = null;
        if (errorBody != null) {
            Matcher matcher = ERROR_FIELDS.matcher(errorBody);
            while (matcher.find()) {
                switch (matcher.group(1)) {
                    case "error" -> error = error == null ? matcher.group(2) : error;
                    case "error_code" -> errorCode = errorCode == null ? matcher.group(2) : errorCode;
                    case "error_description" ->
                            errorDescription = errorDescription == null ? matcher.group(2) : errorDescription;
                    default -> { }
                }
            }
        }
        return new ErrorFields(error, errorCode, errorDescription);
    }

    private static void appendField(StringBuilder summary, String name, String value) {
        if (value == null) {
            return;
        }
        if (summary.length() > 0) {
            summary.append(", ");
        }
        summary.append(name).append('=').append(value);
    }
}
