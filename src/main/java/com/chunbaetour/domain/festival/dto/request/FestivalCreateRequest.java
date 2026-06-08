package com.chunbaetour.domain.festival.dto.request;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 관리자 축제 등록 요청 (KAN-95).
 * 필드 간 불변식(startDate ≤ endDate 등)은 Festival 도메인에서 검증.
 */
public record FestivalCreateRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotBlank @Size(max = 100) String region,
        @NotBlank @Size(max = 255) String address,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 512) String imageUrl,
        @Size(max = 512) String relatedUrl,
        FestivalStatus status  // nullable — null 시 Festival.create() 내부에서 ACTIVE 기본값 적용
) {}
