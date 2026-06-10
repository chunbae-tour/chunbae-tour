package com.chunbaetour.domain.admin.user.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 운영자 사용자 목록 항목 (KAN-180 Admin S02).
 *
 * <p>목록은 식별/상태 위주 최소 필드만 노출. 프로필 이미지/신고누적 등 상세 정보는 단건 상세
 * ({@link UserAdminDetailResponse})에서 제공.
 */
public record UserAdminListResponse(
        Long id,
        @Schema(nullable = true, description = "이메일 — 소셜 가입자는 공급자가 이메일을 제공하지 않은 경우 null")
        String email,
        String nickname,
        Role role,
        AccountStatus status,
        LocalDateTime suspendedUntil,
        LocalDateTime createdAt
) {

    public static UserAdminListResponse from(Account account) {
        return new UserAdminListResponse(
                account.getId(),
                account.getEmail(),
                account.getNickname(),
                account.getRole(),
                account.getStatus(),
                account.getSuspendedUntil(),
                account.getCreatedAt());
    }
}
