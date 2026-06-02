package com.chunbaetour.domain.admin.shop.dto.request;

import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운영자 가게 partial update 요청 (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>모든 필드 nullable — null = 미수정 (KAN-127 Account.updateProfile 패턴). 빈 body({@code {}})는 미변경.
 *
 * <p>{@code status}는 ACTIVE/SUSPENDED만 허용 — CLOSED(폐업) 직접 지정은 서비스에서 INVALID_INPUT_VALUE로 차단.
 * status=ACTIVE → {@code Shop.activate()}, status=SUSPENDED → {@code Shop.hide()}로 전이(결정 B: HIDDEN 미도입).
 *
 * @param status         가게 상태 (nullable). ACTIVE/SUSPENDED만 유효, CLOSED 거부.
 * @param description    가게 소개 (nullable, 빈문자 차단).
 * @param phone          연락처 (nullable, 빈문자 차단).
 * @param operatingHours 운영시간 (nullable, 빈문자 차단).
 */
public record AdminShopUpdateRequest(
        ShopStatus status,
        // description/operatingHours는 줄바꿈을 포함한 여러 줄 입력이 정상이다. Java 정규식의 '.'은 기본적으로
        // 개행을 매치하지 않으므로 ".*\S.*"만 쓰면 "1줄\n2줄" 같은 멀티라인 값이 (내용이 있어도) 거부된다.
        // (?s)(DOTALL)로 '.'이 개행도 매치하게 해 멀티라인 + 공백-only 차단을 동시에 만족시킨다.
        @Size(min = 1, max = 65535, message = "소개글은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "소개글은 공백만 입력할 수 없습니다.") String description,
        // phone은 단일 라인이라 DOTALL 불필요 — 멀티라인 연락처는 허용하지 않는다.
        @Size(min = 1, max = 20, message = "연락처는 빈 값일 수 없습니다.")
        @Pattern(regexp = ".*\\S.*", message = "연락처는 공백만 입력할 수 없습니다.") String phone,
        @Size(min = 1, max = 100, message = "운영시간은 빈 값일 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "운영시간은 공백만 입력할 수 없습니다.") String operatingHours
) {
}
