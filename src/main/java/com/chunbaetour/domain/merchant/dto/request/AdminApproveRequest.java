package com.chunbaetour.domain.merchant.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * 관리자 상인 신청 승인 요청 DTO (KAN-217).
 * placeId: 승인 시 생성되는 가게에 연결할 장소 ID. 선택값 — null이면 장소 미연결.
 */
public record AdminApproveRequest(
        @Positive Long placeId
) {
}
