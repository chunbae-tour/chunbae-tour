package com.chunbaetour.domain.place.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record MapMarkerRequest(
        @NotNull(message = "남서쪽 위도(swLat)는 필수입니다.")
        @DecimalMin(value = "33.0", message = "위도는 33~43 사이여야 합니다.")
        @DecimalMax(value = "43.0", message = "위도는 33~43 사이여야 합니다.")
        Double swLat,

        @NotNull(message = "남서쪽 경도(swLng)는 필수입니다.")
        @DecimalMin(value = "124.0", message = "경도는 124~132 사이여야 합니다.")
        @DecimalMax(value = "132.0", message = "경도는 124~132 사이여야 합니다.")
        Double swLng,

        @NotNull(message = "북동쪽 위도(neLat)는 필수입니다.")
        @DecimalMin(value = "33.0", message = "위도는 33~43 사이여야 합니다.")
        @DecimalMax(value = "43.0", message = "위도는 33~43 사이여야 합니다.")
        Double neLat,

        @NotNull(message = "북동쪽 경도(neLng)는 필수입니다.")
        @DecimalMin(value = "124.0", message = "경도는 124~132 사이여야 합니다.")
        @DecimalMax(value = "132.0", message = "경도는 124~132 사이여야 합니다.")
        Double neLng
) {
}
