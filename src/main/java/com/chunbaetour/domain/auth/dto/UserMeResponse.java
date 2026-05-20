package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import java.time.LocalDateTime;

/**
 * 마이페이지 본인 정보 조회 응답 (Epic A S1).
 *
 * <p>{@code GET /api/v1/users/me}의 응답 body. {@link Account} 엔티티에서 안전한 필드만 추려 노출한다.
 *
 * <p>노출 정책 (보안 핵심):
 * <ul>
 *   <li><b>절대 노출 금지</b>: {@code password}(BCrypt 해시), {@code deletedAt}, {@code suspendedUntil},
 *       {@code updatedAt} — 내부 감사용/보안 위험 필드</li>
 *   <li><b>본인 응답이라 노출 OK</b>: {@code email} (다른 사용자의 마이페이지 조회 endpoint가 생기면 마스킹 필요)</li>
 *   <li><b>UI 표시용</b>: nickname, profileImageUrl, companionScore, companionReviewCount, createdAt</li>
 *   <li><b>권한 분기용</b>: role, status</li>
 * </ul>
 *
 * <p>{@code language}는 현재 회원가입 시 {@code "ko"} 하드코딩이지만 응답 필드에 포함한다 — i18n Epic에서
 * 동적 설정으로 전환될 때 클라이언트 변경 최소화.
 *
 * @param userId               사용자 ID
 * @param email                이메일 (본인 응답이라 풀 노출)
 * @param nickname             닉네임
 * @param profileImageUrl      프로필 이미지 URL (없으면 null)
 * @param language             언어 코드 (현재 "ko" 하드코딩)
 * @param companionScore       동행 평점 (F-CHAT-006에서 갱신 예정)
 * @param companionReviewCount 동행 리뷰 수
 * @param role                 USER/MERCHANT/ADMIN — 클라이언트 UI 권한 분기용
 * @param status               ACTIVE/SUSPENDED/DELETED — 정지/탈퇴 상태 표시
 * @param createdAt            가입일시
 */
public record UserMeResponse(
        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        String language,
        float companionScore,
        int companionReviewCount,
        Role role,
        AccountStatus status,
        LocalDateTime createdAt
) {

    /**
     * {@link Account} 엔티티로부터 안전한 응답 객체 생성.
     *
     * <p>엔티티 → DTO 변환은 본 메서드만 사용한다. 다른 곳에서 필드 누락 또는 민감 정보 노출 실수를 방지.
     */
    public static UserMeResponse from(Account account) {
        return new UserMeResponse(
                account.getId(),
                account.getEmail(),
                account.getNickname(),
                account.getProfileImageUrl(),
                account.getLanguage(),
                account.getCompanionScore(),
                account.getCompanionReviewCount(),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
