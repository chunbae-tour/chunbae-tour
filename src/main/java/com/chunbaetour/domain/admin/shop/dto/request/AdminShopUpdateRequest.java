package com.chunbaetour.domain.admin.shop.dto.request;

import com.chunbaetour.domain.shop.type.ShopStatus;
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
        @Size(min = 1, max = 65535, message = "소개글은 빈 값일 수 없습니다.") String description,
        @Size(min = 1, max = 20, message = "연락처는 빈 값일 수 없습니다.") String phone,
        @Size(min = 1, max = 100, message = "운영시간은 빈 값일 수 없습니다.") String operatingHours
) {
}
