package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;

/**
 * progressStatus를 제외한 캐시 전용 DTO.
 * Redis 역직렬화를 위해 record(canonical constructor) 사용.
 * progressStatus는 read 시점에 동적 계산 — 캐시 stale 방지.
 */
public record FestivalCacheData(
        Long id,
        String name,
        String description,
        String region,
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        String relatedUrl,
        FestivalStatus status
) {
    public static FestivalCacheData from(Festival f) {
        return new FestivalCacheData(
                f.getId(), f.getName(), f.getDescription(),
                f.getRegion(), f.getAddress(),
                f.getStartDate(), f.getEndDate(),
                f.getImageUrl(), f.getRelatedUrl(),
                f.getStatus());
    }

    public FestivalResponse toResponse(LocalDate today) {
        return new FestivalResponse(
                id, name, description, region, address, startDate, endDate,
                imageUrl, relatedUrl, status,
                FestivalProgressStatus.of(startDate, endDate, today));
    }

    public boolean isActive()  { return status == FestivalStatus.ACTIVE; }
    public boolean isDeleted() { return status == FestivalStatus.DELETED; }
}
