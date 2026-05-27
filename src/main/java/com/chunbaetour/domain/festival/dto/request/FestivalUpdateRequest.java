package com.chunbaetour.domain.festival.dto.request;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 관리자 축제 수정 요청 (KAN-95). PUT — 전체 필드 교체.
 */
public record FestivalUpdateRequest(
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
