package com.chunbaetour.domain.festival.dto.request;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 관리자 축제 등록 요청 (KAN-95).
 * startDate ≤ endDate 검증은 FestivalService에서 수행.
 */
public record FestivalCreateRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotBlank @Size(max = 100) String region,
        @NotBlank @Size(max = 255) String address,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String imageUrl,
        String relatedUrl,
        FestivalStatus status
) {}
