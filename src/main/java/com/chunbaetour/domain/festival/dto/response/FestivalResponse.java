package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 축제 응답 DTO — 사용자 목록·상세·관리자 목록 공용 (KAN-95/97/98).
 * progressStatus는 today 기준 동적 계산.
 * latitude/longitude는 지도 공통 매핑용(엔티티 컬럼 lat/lng) — KAN-322.
 */
public record FestivalResponse(
        Long festivalId,
        String name,
        String description,
        String region,
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        String relatedUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        FestivalStatus status,
        FestivalProgressStatus progressStatus
) {
    public static FestivalResponse of(Festival f, LocalDate today) {
        return new FestivalResponse(
                f.getId(),
                f.getName(),
                f.getDescription(),
                f.getRegion(),
                f.getAddress(),
                f.getStartDate(),
                f.getEndDate(),
                f.getImageUrl(),
                f.getRelatedUrl(),
                f.getLat(),
                f.getLng(),
                f.getStatus(),
                FestivalProgressStatus.of(f.getStartDate(), f.getEndDate(), today)
        );
    }
}
