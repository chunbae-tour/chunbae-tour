package com.chunbaetour.domain.admin.place.dto.request;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 운영자 관광지/전통시장 등록 요청 (Admin Epic KAN-177 S07).
 *
 * <p>필수: name/category/address/lat/lng — {@link Place} 빌더의 필수 인자와 일치. 나머지는 선택.
 * 위경도 범위 검증은 빌더에서도 수행하지만, 잘못된 입력을 400(검증 실패)로 일관되게 돌려주기 위해
 * Bean Validation으로 1차 차단한다.
 *
 * @param name           이름 (필수)
 * @param category       카테고리 — TOURIST_SPOT/TRADITIONAL_MARKET (필수)
 * @param description    소개 (선택)
 * @param address        주소 (필수)
 * @param lat            위도 (필수, -90~90)
 * @param lng            경도 (필수, -180~180)
 * @param thumbnailUrl   썸네일 URL (선택)
 * @param imageUrls      이미지 URL JSON 배열 문자열 (선택)
 * @param operatingHours 운영시간 (선택)
 * @param closedDays     휴무일 (선택)
 * @param phone          연락처 (선택)
 * @param admissionFee   입장료 (선택)
 * @param tags           태그 JSON 배열 문자열 (선택)
 */
public record AdminPlaceCreateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 최대 100자까지 입력 가능합니다.") String name,

        @NotNull(message = "카테고리는 필수입니다.") PlaceCategory category,

        String description,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 최대 255자까지 입력 가능합니다.") String address,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90", message = "위도는 -90에서 90 사이여야 합니다.")
        @DecimalMax(value = "90", message = "위도는 -90에서 90 사이여야 합니다.") BigDecimal lat,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180", message = "경도는 -180에서 180 사이여야 합니다.")
        @DecimalMax(value = "180", message = "경도는 -180에서 180 사이여야 합니다.") BigDecimal lng,

        @Size(max = 500, message = "썸네일 URL은 최대 500자까지 입력 가능합니다.") String thumbnailUrl,

        // JSON 배열 문자열로 저장(TEXT 컬럼). @Size로 수 MB 전송 차단(최대 64KB), @Pattern으로 배열 형태 1차 검증.
        @Size(max = 65535, message = "이미지 URL 목록이 너무 깁니다.")
        @Pattern(regexp = "(?s)^\\[.*\\]$", message = "이미지 URL은 JSON 배열 형식이어야 합니다.") String imageUrls,

        @Size(max = 100, message = "운영시간은 최대 100자까지 입력 가능합니다.") String operatingHours,

        @Size(max = 100, message = "휴무일은 최대 100자까지 입력 가능합니다.") String closedDays,

        @Size(max = 20, message = "연락처는 최대 20자까지 입력 가능합니다.") String phone,

        @Size(max = 50, message = "입장료는 최대 50자까지 입력 가능합니다.") String admissionFee,

        // tags도 JSON 배열 문자열(TEXT). imageUrls와 동일 기준 — 크기 상한 + 배열 형태 검증.
        @Size(max = 65535, message = "태그 목록이 너무 깁니다.")
        @Pattern(regexp = "(?s)^\\[.*\\]$", message = "태그는 JSON 배열 형식이어야 합니다.") String tags
) {

    /** 요청을 {@link Place} 엔티티로 변환한다(빌더 — status는 ACTIVE 고정). */
    public Place toEntity() {
        return Place.builder()
                .name(name)
                .category(category)
                .description(description)
                .address(address)
                .lat(lat)
                .lng(lng)
                .thumbnailUrl(thumbnailUrl)
                .imageUrls(imageUrls)
                .operatingHours(operatingHours)
                .closedDays(closedDays)
                .phone(phone)
                .admissionFee(admissionFee)
                .tags(tags)
                .build();
    }
}
