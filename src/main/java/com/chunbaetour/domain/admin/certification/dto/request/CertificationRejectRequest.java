package com.chunbaetour.domain.admin.certification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 운영자 인증 신청 거절 요청 (KAN-204 Admin S05).
 *
 * @param reason 거절 사유 (필수). ShopCertification.rejectReason에 기록 — 상인이 재신청 시 보강 사항 확인용.
 */
public record CertificationRejectRequest(
        @NotBlank(message = "거절 사유는 필수입니다.")
        @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다.")
        String reason
) {}
