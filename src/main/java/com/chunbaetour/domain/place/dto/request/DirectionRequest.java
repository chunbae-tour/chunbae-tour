package com.chunbaetour.domain.place.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DirectionRequest(
    @NotNull(message = "출발지 위도를 입력해주세요.")
    @DecimalMin(value = "-90.0", message = "올바른 위도 범위가 아닙니다.")
    @DecimalMax(value = "90.0", message = "올바른 위도 범위가 아닙니다.")
    BigDecimal originLat,

    @NotNull(message = "출발지 경도를 입력해주세요.")
    @DecimalMin(value = "-180.0", message = "올바른 경도 범위가 아닙니다.")
    @DecimalMax(value = "180.0", message = "올바른 경도 범위가 아닙니다.")
    BigDecimal originLng,

    @NotNull(message = "도착지 위도를 입력해주세요.")
    @DecimalMin(value = "-90.0", message = "올바른 위도 범위가 아닙니다.")
    @DecimalMax(value = "90.0", message = "올바른 위도 범위가 아닙니다.")
    BigDecimal destLat,

    @NotNull(message = "도착지 경도를 입력해주세요.")
    @DecimalMin(value = "-180.0", message = "올바른 경도 범위가 아닙니다.")
    @DecimalMax(value = "180.0", message = "올바른 경도 범위가 아닙니다.")
    BigDecimal destLng
) {
}
