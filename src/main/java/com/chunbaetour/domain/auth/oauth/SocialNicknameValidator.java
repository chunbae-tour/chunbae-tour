package com.chunbaetour.domain.auth.oauth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * {@link SocialNickname} 검증 구현.
 *
 * <p>한글(가–힣)은 폭 2, 그 외 허용 문자(영문/숫자)는 폭 1로 계산해 총 폭이 2~16 범위인지 본다.
 * 허용 문자셋을 벗어나면(특수문자/공백/이모지/자모 단독 등) 즉시 거부한다.
 */
public class SocialNicknameValidator implements ConstraintValidator<SocialNickname, String> {

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z0-9]+$");
    private static final int MIN_WIDTH = 2;
    private static final int MAX_WIDTH = 16;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null/blank는 @NotBlank가 별도로 처리. 여기서는 형식만 본다(null이면 통과시켜 메시지 중복 방지).
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (!ALLOWED.matcher(value).matches()) {
            return false;
        }
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hangulSyllable = c >= '가' && c <= '힣';
            width += hangulSyllable ? 2 : 1;
        }
        return width >= MIN_WIDTH && width <= MAX_WIDTH;
    }
}
