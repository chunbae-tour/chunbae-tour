package com.chunbaetour.domain.admin.shop.dto.request;

import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운영자 가게 partial update 요청 (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>모든 필드 nullable — null = 미수정 (KAN-127 Account.updateProfile 패턴). 단, 전 필드가 null인
 * 빈 body({@code {}})는 의미 없는 요청이므로 컨트롤러 진입부에서 {@code INVALID_INPUT_VALUE}(400)로 거부한다
 * ({@link #isEmpty()}). KAN-127 사용자 PATCH는 빈 body를 200 no-op으로 두지만, 운영자 가게 수정은
 * audit(SHOP_UPDATE) 대상이라 "아무 것도 안 바꾸는 수정"을 명시적 오류로 처리한다.
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

    /**
     * 전 필드가 null인 빈 요청 여부. {@code @Valid}는 null 필드를 통과시키므로(각 제약이 null-friendly)
     * 빈 body는 Bean Validation으로 걸러지지 않는다. 컨트롤러가 본 메서드로 따로 판별해 400으로 거부한다.
     */
    public boolean isEmpty() {
        return status == null && description == null && phone == null && operatingHours == null;
    }
}
