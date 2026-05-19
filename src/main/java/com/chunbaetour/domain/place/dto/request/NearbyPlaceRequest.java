package com.chunbaetour.domain.place.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NearbyPlaceRequest {
    
    @NotNull(message = "위도는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다.")
    private Double lat;

    @NotNull(message = "경도는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다.")
    private Double lng;

    @NotNull(message = "반경(m)은 필수입니다.")
    @DecimalMin(value = "100.0", message = "반경은 100m 이상이어야 합니다.")
    @DecimalMax(value = "20000.0", message = "반경은 20000m 이하이어야 합니다.")
    private Double radius;
    
    private Long cursor; // null이면 첫 페이지
    private Double cursorDistance; // 복합 커서를 위한 이전 페이지 마지막 아이템의 거리

    @JsonIgnore
    @AssertTrue(message = "cursor와 cursorDistance는 함께 전달되어야 합니다.")
    public boolean isCursorPairValid() {
        return (cursor == null) == (cursorDistance == null);
    }

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 50, message = "size는 50 이하이어야 합니다.")
    private int size = 10;
}

