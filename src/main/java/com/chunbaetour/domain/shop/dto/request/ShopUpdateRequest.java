package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가게 수정 요청 DTO (STORY-10).
 * 위치(address/lat/lng)는 수정 불가 — 관리자 처리.
 * null 필드는 수정하지 않음 (부분 수정 지원).
 */
public record ShopUpdateRequest(
        @Size(max = 50) String shopName,
        @Size(max = 50) String category,
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$") String phone,
        @Size(max = 500) String description,
        @Size(max = 100) String operatingHours,
        @Size(max = 100) String closedDays,
        String imageUrls
) {
}
