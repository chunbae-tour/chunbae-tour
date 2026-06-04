package com.chunbaetour.domain.merchant.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 관리자 상인 신청 승인 요청 DTO (KAN-217).
 * placeId: 승인 시 생성되는 가게에 연결할 장소 ID. 모든 가게는 반드시 Place에 속해야 함.
 */
public record AdminApproveRequest(
        @NotNull Long placeId
) {
}
