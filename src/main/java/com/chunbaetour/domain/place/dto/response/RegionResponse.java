package com.chunbaetour.domain.place.dto.response;

/**
 * 프론트엔드로 반환하는 행정구역 응답 DTO
 *
 * @param depth1      시/도 (예: 서울특별시)
 * @param depth2      구/군 (예: 송파구)
 * @param depth3      동/읍/면 (예: 잠실동)
 * @param fullAddress 전체 주소 (예: 서울특별시 송파구 잠실동)
 */
public record RegionResponse(
        String depth1,
        String depth2,
        String depth3,
        String fullAddress
) {
}
