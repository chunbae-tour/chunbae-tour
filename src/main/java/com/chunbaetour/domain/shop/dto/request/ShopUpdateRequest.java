package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가게 수정 요청 DTO (STORY-10).
 * 위치(address/lat/lng)는 수정 불가 — 관리자 처리.
 * null 필드는 수정하지 않음 (부분 수정 지원).
 */
public record ShopUpdateRequest(
        // null = 수정 안 함, "" = min=1로 거부 — 빈 가게명/카테고리 저장 방지
        @Size(min = 1, max = 50) String shopName,
        @Size(min = 1, max = 50) String category,
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$") String phone,
        // null = 수정 안 함, "" = 소개글 삭제 허용 — 가게명/카테고리와 달리 빈 소개글은 유효한 상태
        @Size(max = 500) String description,
        // null = 수정 안 함, "" = min=1로 거부 — 운영시간/휴무일 빈 값 노출 방지
        @Size(min = 1, max = 100) String operatingHours,
        @Size(min = 1, max = 100) String closedDays,
        // null = 수정 안 함, 값 있으면 기존 이미지 목록 전체 교체 — 단건 추가/삭제 아님
        // JSON 배열 전체 문자열 길이 제한 — URL 1개가 아닌 모든 URL 합산 (URL 1개 ≈ 70자, 약 25장 커버)
        @Size(max = 2000) String imageUrls
) {
}
