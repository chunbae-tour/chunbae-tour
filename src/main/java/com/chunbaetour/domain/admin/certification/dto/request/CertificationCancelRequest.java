package com.chunbaetour.domain.admin.certification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 운영자 인증 취소 요청 (KAN-204 Admin S05).
 *
 * @param reason 취소 사유 (필수). Shop.unmarkCertified()로 인증 마크를 회수하며 사유를 admin_action_log에 기록.
 */
public record CertificationCancelRequest(
        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
        String reason
) {}
