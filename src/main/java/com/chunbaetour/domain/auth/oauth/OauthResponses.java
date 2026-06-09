package com.chunbaetour.domain.auth.oauth;

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
}
