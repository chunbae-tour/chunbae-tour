package com.chunbaetour.domain.place.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record NearbyPlaceRequest(
    @NotNull(message = "위도는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다.")
    Double lat,

    @NotNull(message = "경도는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다.")
    Double lng,

    @NotNull(message = "반경(m)은 필수입니다.")
    @DecimalMin(value = "100.0", message = "반경은 100m 이상이어야 합니다.")
    @DecimalMax(value = "20000.0", message = "반경은 20000m 이하이어야 합니다.")
    Double radius,
    @Min(value = 0, message = "page는 0 이상이어야 합니다.")
    Integer page,

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 50, message = "size는 50 이하이어야 합니다.")
    Integer size
) {
    public NearbyPlaceRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 10;
        }
    }
}

