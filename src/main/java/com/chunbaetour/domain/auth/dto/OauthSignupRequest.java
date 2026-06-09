package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.oauth.SocialNickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 소셜 로그인 2단계(신규 가입 추가정보) 요청.
 *
 * <p>1단계에서 받은 {@code ticket}(provider+oauthId 서명 토큰)과 함께 사용자가 입력한 프로필을 제출한다.
 * 중복가입 방지 기준은 전화번호(서버에서 숫자만 정규화 후 UNIQUE 검사).
 *
 * @param ticket    1단계 응답의 {@code signupTicket}
 * @param name      실명
 * @param phone     휴대전화번호 (하이픈 유무 무관 — 서버가 숫자만 추출해 저장/중복검사)
 * @param birthdate 생년월일 (과거 일자) — 저장만, 기능 제한 없음
 * @param nickname  닉네임 (한글 8자 / 영문·숫자 16자 폭, 특수문자·공백 불가)
 *
 * <p><b>이메일 미포함</b>: 계정 이메일은 클라이언트 입력이 아니라 1단계 티켓에 서명돼 온 <b>공급자 검증
 * 이메일</b>을 사용한다(임의 이메일 선점 방지). 따라서 본 요청에 email은 받지 않는다.
 */
public record OauthSignupRequest(
        @NotBlank
        String ticket,

        @NotBlank
        @Size(max = 50)
        String name,

        @NotBlank
        @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "휴대전화번호 형식이 올바르지 않습니다.")
        String phone,

        @NotNull
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthdate,

        @NotBlank
        @SocialNickname
        String nickname
) {
}
