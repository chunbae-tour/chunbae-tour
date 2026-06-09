package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SocialNicknameValidator} 단위 테스트 — 한글 8자 / 영문·숫자 16자 폭 + 특수문자·공백 불가.
 */
class SocialNicknameValidatorTest {

    private final SocialNicknameValidator validator = new SocialNicknameValidator();

    @DisplayName("유효: 한글 8자 이내, 영문/숫자 16자 이내(폭 ≤16), 혼합")
    @ParameterizedTest
    @ValueSource(strings = {
            "춘배",              // 한글 2자 (폭 4)
            "춘배투어가이드맵",    // 한글 8자 (폭 16, 경계)
            "abcdefghijklmnop",  // 영문 16자 (폭 16, 경계)
            "user01",            // 영문+숫자
            "춘배tour"           // 혼합 (폭 4 + 4 = 8)
    })
    void valid(String nickname) {
        assertThat(validator.isValid(nickname, null)).isTrue();
    }

    @DisplayName("무효: 폭 초과 / 특수문자 / 공백 / 이모지")
    @ParameterizedTest
    @ValueSource(strings = {
            "춘배투어가이드맵스",   // 한글 9자 (폭 18 > 16)
            "abcdefghijklmnopq",  // 영문 17자 (폭 17 > 16)
            "춘배투어가이드guide",  // 혼합: 한글 6자(폭 12) + 영문 5자(폭 5) = 폭 17 > 16
            "춘배_투어",            // 언더스코어(특수문자)
            "춘배 투어",            // 공백
            "nick!name",          // 특수문자
            "춘배😀"               // 이모지
    })
    void invalid(String nickname) {
        assertThat(validator.isValid(nickname, null)).isFalse();
    }

    @DisplayName("null/빈값은 @NotBlank가 처리 — 여기선 통과(메시지 중복 방지)")
    @ParameterizedTest
    @ValueSource(strings = {""})
    void blankPassesThrough(String nickname) {
        assertThat(validator.isValid(nickname, null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
