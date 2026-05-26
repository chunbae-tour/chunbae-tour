package com.chunbaetour.domain.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/users/me 요청 body — partial update semantics (Epic A S2, KAN-127).
 *
 * <p>모든 필드는 nullable. 보낸 필드만 갱신되고 null/누락 필드는 변경되지 않는다.
 * Bean Validation 어노테이션은 값이 존재할 때만 적용 — null이면 검증 skip (jakarta validation default).
 *
 * <p><b>본 요청에 부재한 필드 (의도적 제외)</b>:
 * <ul>
 *   <li>{@code email} — 변경은 인증 메일 재발송 등 별도 정책 필요. 별도 PRD.</li>
 *   <li>{@code password} — 비밀번호 변경 흐름은 별도 도메인 (현재 비번 + 새 비번 검증 + 알림 등).</li>
 *   <li>{@code role}/{@code status} — 시스템 갱신 필드. 운영자 도메인에서만 조작.</li>
 *   <li>{@code companionScore}/{@code companionReviewCount} — 시스템 자동 갱신.</li>
 * </ul>
 * 자료형에 부재 → 컴파일 단계에서 우발적 노출 차단.
 *
 * @param nickname        새 닉네임. 한글/영문/숫자/언더스코어/하이픈 2~20자.
 *                        unique 검증은 서비스 레이어에서 본인 제외 중복 체크로 보강.
 * @param language        새 언어 코드. ISO 639-1 화이트리스트 (현재 ko/en/ja 허용).
 *                        Pattern 정규식으로 형식 + 길이 1차 차단.
 * @param profileImageUrl 새 프로필 이미지 URL. http/https 스킴 + max 500자.
 *                        null이면 변경 안 함 (이미지 제거 요청은 별도 정책 — 빈 문자열 vs null 구분 필요 시 후속).
 */
public record PatchUserMeRequest(

        @Size(min = 2, max = 20)
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]{2,20}$",
                message = "닉네임은 한글/영문/숫자/_/-만 가능하며 2~20자여야 합니다.")
        String nickname,

        @Pattern(regexp = "^(ko|en|ja)$",
                message = "language는 ko/en/ja 중 하나여야 합니다.")
        String language,

        @Size(max = 500)
        @Pattern(regexp = "^https?://.+",
                message = "profileImageUrl은 http:// 또는 https://로 시작하는 URL이어야 합니다.")
        String profileImageUrl
) {

    /**
     * 모든 필드가 null이면 noop 요청. Service가 빠른 return으로 불필요한 DB 호출 회피.
     */
    public boolean isEmpty() {
        return nickname == null && language == null && profileImageUrl == null;
    }
}
