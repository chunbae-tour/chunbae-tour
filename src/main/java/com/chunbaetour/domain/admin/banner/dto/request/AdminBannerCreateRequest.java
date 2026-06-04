package com.chunbaetour.domain.admin.banner.dto.request;

import com.chunbaetour.domain.banner.Banner;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 운영자 배너 등록 요청 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>필수: title/imageUrl — {@link Banner} 빌더의 필수 인자와 일치. linkUrl/priority/노출 기간은 선택.
 * priority가 null이면 빌더에서 0으로 기본 처리된다.
 *
 * <p>노출 기간 역전(startDate > endDate)은 {@link #validPeriod()} {@code @AssertTrue}로 400 차단한다 —
 * 빌더에서도 검증하지만 잘못된 입력을 일관되게 검증 실패(400)로 돌려주기 위해 1차로 막는다.
 *
 * @param title     배너 제목 (필수)
 * @param imageUrl  배너 이미지 URL (필수)
 * @param linkUrl   클릭 이동 링크 (선택)
 * @param priority  노출 우선순위 — 작을수록 먼저 노출 (선택, null이면 0)
 * @param startDate 노출 시작일 (선택)
 * @param endDate   노출 종료일 (선택)
 */
public record AdminBannerCreateRequest(
        @NotBlank(message = "배너 제목은 필수입니다.")
        @Size(max = 100, message = "배너 제목은 최대 100자까지 입력 가능합니다.") String title,

        @NotBlank(message = "배너 이미지 URL은 필수입니다.")
        @Size(max = 500, message = "이미지 URL은 최대 500자까지 입력 가능합니다.") String imageUrl,

        @Size(max = 500, message = "링크 URL은 최대 500자까지 입력 가능합니다.") String linkUrl,

        Integer priority,

        LocalDate startDate,

        LocalDate endDate
) {

    /** 노출 기간 불변식 — 둘 다 지정된 경우에만 startDate <= endDate 요구(하나라도 null이면 통과). */
    @AssertTrue(message = "노출 시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isValidPeriod() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    /** 요청을 {@link Banner} 엔티티로 변환한다(빌더 — status는 ACTIVE 고정). */
    public Banner toEntity() {
        return Banner.builder()
                .title(title)
                .imageUrl(imageUrl)
                .linkUrl(linkUrl)
                .priority(priority)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
