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
}
