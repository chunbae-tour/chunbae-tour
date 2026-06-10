package com.chunbaetour.domain.place.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MapMarkerRequest(
        @NotNull(message = "남서쪽 위도(swLat)는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90~90 사이여야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90~90 사이여야 합니다.")
        BigDecimal swLat,

        @NotNull(message = "남서쪽 경도(swLng)는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180~180 사이여야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180~180 사이여야 합니다.")
        BigDecimal swLng,

        @NotNull(message = "북동쪽 위도(neLat)는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90~90 사이여야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90~90 사이여야 합니다.")
        BigDecimal neLat,

        @NotNull(message = "북동쪽 경도(neLng)는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180~180 사이여야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180~180 사이여야 합니다.")
        BigDecimal neLng
) {
    @AssertTrue(message = "남서쪽 좌표는 북동쪽 좌표보다 작거나 같아야 합니다.")
    public boolean isValidBoundingBox() {
        return swLat != null && swLng != null && neLat != null && neLng != null
                && swLat.compareTo(neLat) <= 0
                && swLng.compareTo(neLng) <= 0;
    }

    private static final BigDecimal MAX_LAT_SPAN = new BigDecimal("2.0");
    private static final BigDecimal MAX_LNG_SPAN = new BigDecimal("2.0");

    @AssertTrue(message = "지도 조회 범위가 너무 넓습니다. 지도를 확대한 뒤 다시 조회해주세요.")
    public boolean isViewportSpanAllowed() {
        if (swLat == null || swLng == null || neLat == null || neLng == null) {
            return true; // null 검증은 @NotNull이 처리
        }

        return neLat.subtract(swLat).compareTo(MAX_LAT_SPAN) <= 0
                && neLng.subtract(swLng).compareTo(MAX_LNG_SPAN) <= 0;
    }
}
