package com.chunbaetour.domain.shop.dto.request;

/**
 * 관리자 가게-장소 수동 연결 요청 DTO (KAN-217).
 * placeId: 연결할 장소 ID. null이면 기존 연결 해제.
 */
public record AdminShopPlaceRequest(
        Long placeId
) {
}
