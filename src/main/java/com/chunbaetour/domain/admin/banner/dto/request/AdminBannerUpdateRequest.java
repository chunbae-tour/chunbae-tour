package com.chunbaetour.domain.admin.banner.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 운영자 배너 partial update 요청 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>모든 필드 nullable — null = 미수정 ({@code Banner.update(...)} null-skip 패턴). 전 필드가 null인 빈
 * body({@code {}})는 의미 없는 수정이므로 컨트롤러 진입부에서 {@code INVALID_INPUT_VALUE}(400)로 거부한다
 * ({@link #isEmpty()}) — audit(BANNER_UPDATE) 대상이라 "아무 것도 안 바꾸는 수정"을 명시적 오류로 둔다.
 *
 * <p>null-skip의 한계 — 본 슬라이스는 "값 클리어"(linkUrl/노출 기간을 명시적으로 null로 비우기)를 지원하지 않는다.
 * null은 항상 "미수정"으로 해석한다(YAGNI). 운영에서 링크 제거/상시 노출 전환이 실제로 필요해지면 후속 슬라이스에서
 * JsonNullable 등으로 "미전달 vs null 전달"을 구분한다.
 *
 * <p>status는 본 요청의 수정 범위 밖이다 — 노출/숨김/삭제 상태 전이는 별도 endpoint(DELETE soft delete) 책임이라
 * {@link #isEmpty()} 판정 필드에도 포함하지 않는다.
 *
 * <p>공백 차단(S04/S07 리뷰 교훈): {@code (?s)}(DOTALL)로 멀티라인 값도 공백-only는 거부한다. title/url은 단일
 * 라인이지만 일관성을 위해 동일 패턴을 적용한다.
 *
 * <p>노출 기간 역전은 {@link #isValidPeriod()}로 막되, 본 DTO는 요청에 둘 다 들어온 경우만 검증할 수 있다 —
 * 한쪽만 수정해 기존 값과 역전되는 경우는 {@code Banner.update(...)}가 병합 후 재검증한다.
 */
public record AdminBannerUpdateRequest(
        @Size(min = 1, max = 100, message = "배너 제목은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "배너 제목은 공백만 입력할 수 없습니다.") String title,

        @Size(min = 1, max = 500, message = "이미지 URL은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "이미지 URL은 공백만 입력할 수 없습니다.") String imageUrl,

        @Size(min = 1, max = 500, message = "링크 URL은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "링크 URL은 공백만 입력할 수 없습니다.") String linkUrl,

        Integer priority,

        LocalDate startDate,

        LocalDate endDate
) {

    /** 요청 내에 둘 다 들어온 경우의 노출 기간 불변식(한쪽만 수정 시 병합 검증은 도메인 책임). */
    @AssertTrue(message = "노출 시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isValidPeriod() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    /**
     * 전 필드가 null인 빈 요청 여부. {@code @Valid}는 null 필드를 통과시키므로 빈 body는 Bean Validation으로
     * 걸러지지 않는다. 컨트롤러가 본 메서드로 따로 판별해 400으로 거부한다.
     */
    public boolean isEmpty() {
        return title == null && imageUrl == null && linkUrl == null
                && priority == null && startDate == null && endDate == null;
    }
}
