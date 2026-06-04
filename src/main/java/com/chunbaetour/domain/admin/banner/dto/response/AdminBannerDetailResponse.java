package com.chunbaetour.domain.admin.banner.dto.response;

import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.type.BannerStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영자 배너 상세 응답 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>등록/수정 결과로도 반환된다 — partial update에서 미지정 필드 보존을 외부 동작으로 확인할 수 있게 전체 편집
 * 필드 + 우선순위/노출 기간/상태를 노출한다.
 */
public record AdminBannerDetailResponse(
        Long id,
        String title,
        String imageUrl,
        String linkUrl,
        int priority,
        LocalDate startDate,
        LocalDate endDate,
        BannerStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminBannerDetailResponse from(Banner banner) {
        return new AdminBannerDetailResponse(
                banner.getId(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.getPriority(),
                banner.getStartDate(),
                banner.getEndDate(),
                banner.getStatus(),
                banner.getCreatedAt(),
                banner.getUpdatedAt());
    }
}
