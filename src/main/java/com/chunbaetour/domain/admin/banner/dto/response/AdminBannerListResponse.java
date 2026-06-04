package com.chunbaetour.domain.admin.banner.dto.response;

import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.type.BannerStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영자 배너 목록 항목 응답 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>목록은 식별/정렬/노출 판단에 필요한 요약 필드만 노출한다(상세는 {@link AdminBannerDetailResponse}).
 */
public record AdminBannerListResponse(
        Long id,
        String title,
        String imageUrl,
        int priority,
        LocalDate startDate,
        LocalDate endDate,
        BannerStatus status,
        LocalDateTime createdAt
) {

    public static AdminBannerListResponse from(Banner banner) {
        return new AdminBannerListResponse(
                banner.getId(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getPriority(),
                banner.getStartDate(),
                banner.getEndDate(),
                banner.getStatus(),
                banner.getCreatedAt());
    }
}
