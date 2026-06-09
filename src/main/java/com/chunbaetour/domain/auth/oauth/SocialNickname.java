package com.chunbaetour.domain.auth.oauth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 소셜 가입 닉네임 규칙 검증.
 *
 * <ul>
 *   <li>허용 문자: 한글·영문·숫자만 (특수문자·기호·공백·이모지 불가)</li>
 *   <li>길이: 폭 가중 ≤ 16 (한글 1자 = 2, 영문·숫자 1자 = 1) → 한글 8자 또는 영문 16자가 같은 폭</li>
 *   <li>최소 폭 2</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = SocialNicknameValidator.class)
@Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SocialNickname {

    String message() default "닉네임은 한글 8자(영문·숫자 16자) 이내여야 하며 특수문자·공백은 사용할 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
