package com.chunbaetour.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NearbyPlaceRequest {
    
    @NotNull(message = "위도는 필수입니다.")
    @Min(value = -90, message = "위도는 -90 이상이어야 합니다.")
    @Max(value = 90, message = "위도는 90 이하이어야 합니다.")
    private Double lat;

    @NotNull(message = "경도는 필수입니다.")
    @Min(value = -180, message = "경도는 -180 이상이어야 합니다.")
    @Max(value = 180, message = "경도는 180 이하이어야 합니다.")
    private Double lng;

    @NotNull(message = "반경(m)은 필수입니다.")
    @Min(value = 100, message = "반경은 100m 이상이어야 합니다.")
    @Max(value = 20000, message = "반경은 20000m 이하이어야 합니다.")
    private Double radius;
    
    private Long cursor; // null이면 첫 페이지
    
    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 50, message = "size는 50 이하이어야 합니다.")
    private int size = 10;
}

