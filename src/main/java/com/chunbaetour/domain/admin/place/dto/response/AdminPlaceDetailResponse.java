package com.chunbaetour.domain.admin.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 운영자 관광지 상세 응답 (Admin Epic KAN-177 S07).
 *
 * <p>등록/수정 결과로도 반환된다 — 변경되지 않은 필드 보존(partial update) 검증을 외부 동작으로 확인할 수 있게
 * 전체 편집 필드 + 위경도/카테고리/상태를 노출한다.
 */
public record AdminPlaceDetailResponse(
        Long id,
        String name,
        PlaceCategory category,
        String description,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String thumbnailUrl,
        String imageUrls,
        String operatingHours,
        String closedDays,
        String phone,
        String admissionFee,
        String tags,
        PlaceStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminPlaceDetailResponse from(Place place) {
        return new AdminPlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getDescription(),
                place.getAddress(),
                place.getLat(),
                place.getLng(),
                place.getThumbnailUrl(),
                place.getImageUrls(),
                place.getOperatingHours(),
                place.getClosedDays(),
                place.getPhone(),
                place.getAdmissionFee(),
                place.getTags(),
                place.getStatus(),
                place.getCreatedAt(),
                place.getUpdatedAt());
    }
}
