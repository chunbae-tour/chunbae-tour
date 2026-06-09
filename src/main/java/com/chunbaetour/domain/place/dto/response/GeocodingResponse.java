package com.chunbaetour.domain.place.dto.response;

import java.math.BigDecimal;

/**
 * 주소 → 좌표 변환(지오코딩) API 응답 DTO.
 *
 * @param addressName 카카오가 반환한 정제된 주소명 (도로명 주소 우선, 없으면 지번 주소)
 * @param lat         위도
 * @param lng         경도
 */
public record GeocodingResponse(
        String addressName,
        BigDecimal lat,
        BigDecimal lng
) {}
