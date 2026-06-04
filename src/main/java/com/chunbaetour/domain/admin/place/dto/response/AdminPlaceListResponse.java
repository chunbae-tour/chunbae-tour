package com.chunbaetour.domain.admin.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.time.LocalDateTime;

/**
 * 운영자 관광지 목록 항목 응답 (Admin Epic KAN-177 S07).
 *
 * <p>목록은 식별/검색에 필요한 요약 필드만 노출한다(상세는 {@link AdminPlaceDetailResponse}).
 */
public record AdminPlaceListResponse(
        Long id,
        String name,
        PlaceCategory category,
        String address,
        PlaceStatus status,
        LocalDateTime createdAt
) {

    public static AdminPlaceListResponse from(Place place) {
        return new AdminPlaceListResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getStatus(),
                place.getCreatedAt());
    }
}
