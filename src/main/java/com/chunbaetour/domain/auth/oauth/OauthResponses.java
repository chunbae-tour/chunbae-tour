package com.chunbaetour.domain.auth.oauth;

import java.util.Locale;
import java.util.Map;

/**
 * 소셜 공급자 JSON 응답(Map 형태)에서 값을 안전하게 추출하는 헬퍼.
 *
 * <p>공급자별 응답 스키마가 제각각이고 일부 필드는 동의 범위에 따라 누락되므로, 강타입 DTO 대신 Map을
 * 방어적으로 탐색한다. id가 숫자/문자 어느 쪽으로 와도 문자열로 정규화한다.
 */
final class OauthResponses {

    private OauthResponses() {
    }

    static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * 토큰 에러 본문(공급자 {@code error}/{@code error_code} 등)에 주어진 키워드 중 하나라도
     * 포함되는지 대소문자 무시로 판별. 인가코드 무효(invalid_grant/KOE320 등 — 사용자 재시도로 해소)인지
     * 가려 4xx(OAUTH_INVALID_AUTHORIZATION)와 502(OAUTH_PROVIDER_ERROR)를 분기하는 데 쓴다.
     * 카카오/네이버가 키워드만 다를 뿐 판별 로직이 동일해 공통화한다.
     */
    static boolean containsAnyIgnoreCase(String body, String... keywords) {
        if (body == null || body.isBlank() || keywords == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            // blank 키워드는 모든 본문과 매치돼 오분류(전부 400)를 만들므로 건너뛴다.
            if (keyword != null && !keyword.isBlank()
                    && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
