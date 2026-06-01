package com.chunbaetour.domain.admin.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 운영자 사용자 정지 요청 (KAN-180 Admin S02).
 *
 * @param reason       정지 사유 (필수). admin_action_logs 및 Account.suspendedReason에 기록.
 * @param durationDays 정지 기간(일). null = 무기한 정지(suspendedUntil null). 지정 시 1 이상.
 *                     서비스가 현재 시각 + durationDays로 suspendedUntil을 산출.
 */
public record UserSuspendRequest(
        @NotBlank(message = "정지 사유는 필수입니다.")
        @Size(max = 500, message = "정지 사유는 500자 이하여야 합니다.")
        String reason,
        @Min(value = 1, message = "정지 기간은 1일 이상이어야 합니다.") Integer durationDays
) {}
