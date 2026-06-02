package com.chunbaetour.domain.admin.place.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운영자 관광지/전통시장 partial update 요청 (Admin Epic KAN-177 S07).
 *
 * <p>모든 필드 nullable — null = 미수정 ({@code Place.update(...)} null-skip 패턴, KAN-127 미러). 전 필드가
 * null인 빈 body({@code {}})는 의미 없는 수정이므로 컨트롤러 진입부에서 {@code INVALID_INPUT_VALUE}(400)로
 * 거부한다({@link #isEmpty()}) — audit(PLACE_UPDATE) 대상이라 "아무 것도 안 바꾸는 수정"을 명시적 오류로 둔다.
 *
 * <p>공백 차단(S04 리뷰 교훈): 멀티라인 값(description 등)은 {@code (?s)}(DOTALL)로 '.'이 개행도 매치하게 해
 * 공백-only는 거부하되 정상 멀티라인은 허용한다. 단일 라인 필드는 DOTALL 불필요.
 *
 * <p>본 슬라이스 수정 범위는 {@code Place.update(...)}가 받는 필드로 한정한다(category/lat/lng/thumbnail/
 * imageUrls는 등록 전용 — 위치/카테고리 변경은 본 endpoint 범위 밖).
 */
public record AdminPlaceUpdateRequest(
        @Size(min = 1, max = 100, message = "이름은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백만 입력할 수 없습니다.") String name,

        @Size(min = 1, max = 65535, message = "소개글은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "소개글은 공백만 입력할 수 없습니다.") String description,

        @Size(min = 1, max = 255, message = "주소는 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "주소는 공백만 입력할 수 없습니다.") String address,

        @Size(min = 1, max = 100, message = "운영시간은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "운영시간은 공백만 입력할 수 없습니다.") String operatingHours,

        @Size(min = 1, max = 100, message = "휴무일은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "휴무일은 공백만 입력할 수 없습니다.") String closedDays,

        @Size(min = 1, max = 20, message = "연락처는 빈 값일 수 없습니다.")
        @Pattern(regexp = ".*\\S.*", message = "연락처는 공백만 입력할 수 없습니다.") String phone,

        @Size(min = 1, max = 50, message = "입장료는 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "입장료는 공백만 입력할 수 없습니다.") String admissionFee,

        @Size(min = 1, max = 65535, message = "태그는 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "태그는 공백만 입력할 수 없습니다.") String tags
) {

    /**
     * 전 필드가 null인 빈 요청 여부. {@code @Valid}는 null 필드를 통과시키므로 빈 body는 Bean Validation으로
     * 걸러지지 않는다. 컨트롤러가 본 메서드로 따로 판별해 400으로 거부한다.
     */
    public boolean isEmpty() {
        return name == null && description == null && address == null
                && operatingHours == null && closedDays == null
                && phone == null && admissionFee == null && tags == null;
    }
}
