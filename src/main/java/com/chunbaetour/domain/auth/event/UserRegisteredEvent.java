package com.chunbaetour.domain.auth.event;

/**
 * 회원가입 완료 이벤트 — 로컬(이메일/비번) 가입과 소셜(카카오/네이버) 가입 모두 발행.
 *
 * @param userId   가입한 계정 id (항상 non-null)
 * @param email    이메일. <b>nullable</b> — 소셜 가입자는 공급자가 이메일을 제공하지 않으면(예: 카카오
 *                 이메일 미동의) null이다. 이메일 발송/CRM 연동 등 새 리스너는 반드시 null 분기할 것.
 * @param nickname 닉네임 (항상 non-null)
 */
public record UserRegisteredEvent(Long userId, String email, String nickname) {
}
