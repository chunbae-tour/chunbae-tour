package com.chunbaetour.domain.admin.user.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import java.time.LocalDateTime;

/**
 * 운영자 사용자 단건 상세 (KAN-180 Admin S02).
 *
 * <p>가입정보 + 정지상태(사유/만료) + 누적 신고 건수를 한 응답에 묶는다. 누적 신고는 report 도메인의
 * 상태 무관 전체 카운트(서비스가 조합).
 */
public record UserAdminDetailResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        Role role,
        AccountStatus status,
        String suspendedReason,
        LocalDateTime suspendedUntil,
        long reportCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserAdminDetailResponse from(Account account, long reportCount) {
        return new UserAdminDetailResponse(
                account.getId(),
                account.getEmail(),
                account.getNickname(),
                account.getProfileImageUrl(),
                account.getRole(),
                account.getStatus(),
                account.getSuspendedReason(),
                account.getSuspendedUntil(),
                reportCount,
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
