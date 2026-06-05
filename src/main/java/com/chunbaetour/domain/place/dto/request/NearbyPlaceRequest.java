package com.chunbaetour.domain.place.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Parameter(description = "검색 반경 (m 단위, 100~20000)")
    Double radius,

    @Parameter(description = "다음 페이지 조회를 위한 커서 (마지막 장소의 ID)")
    Long cursor,

    @Parameter(description = "다음 페이지 조회를 위한 커서 (마지막 장소의 거리)")
    Double cursorDistance,

    @Min(value = 1, message = "크기는 1 이상이어야 합니다.")
    @Max(value = 50, message = "크기는 50을 초과할 수 없습니다.")
    @Parameter(description = "페이지 크기 (기본 10, 최대 50)")
    Integer size
) {
    public NearbyPlaceRequest {
        if (size == null) {
            size = 10;
        }
    }

    @AssertTrue(message = "커서와 커서 거리는 둘 다 제공되거나 둘 다 제공되지 않아야 합니다.")
    @JsonIgnore
    public boolean isCursorPairValid() {
        return (cursor == null && cursorDistance == null) || (cursor != null && cursorDistance != null);
    }
}

