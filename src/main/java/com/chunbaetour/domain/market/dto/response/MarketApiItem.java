package com.chunbaetour.domain.market.dto.response;

import com.chunbaetour.domain.common.util.RegionParser;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터포털 전통시장 API 응답 아이템.
 * 개별 시장 데이터를 담는 DTO.
 */
@Getter
@NoArgsConstructor
public class MarketApiItem {

    /** 시장명 */
    @JsonProperty("mrktNm")
    public String mrktNm;

    /** 시장유형 (상설장, 4일장 등) */
    @JsonProperty("mrktType")
    public String mrktType;

    /** 도로명주소 */
    @JsonProperty("rdnmadr")
    public String rdnmadr;

    /** 위도 */
    @JsonProperty("latitude")
    public String latitude;

    /** 경도 */
    @JsonProperty("longitude")
    public String longitude;

    /** 전화번호 */
    @JsonProperty("phoneNumber")
    public String phoneNumber;

    /** 홈페이지 주소 */
    @JsonProperty("homepageUrl")
    public String homepageUrl;

    /** 개설년도 */
    @JsonProperty("estblYear")
    public String estblYear;

    /**
     * API 응답을 TraditionalMarket 엔티티로 변환.
     * latitude, longitude는 String → BigDecimal 변환.
     * estblYear는 String → Integer 변환 (파싱 불가 시 null — 연도 이상값으로 시장 전체 skip 방지).
     */
    public TraditionalMarket toEntity() {
        BigDecimal latValue = latitude != null && !latitude.isBlank()
            ? new BigDecimal(latitude)
            : null;
        BigDecimal lngValue = longitude != null && !longitude.isBlank()
            ? new BigDecimal(longitude)
            : null;

        // 전통시장 API는 지역코드가 없어 도로명주소(rdnmadr)에서 시도/시군구를 파싱한다.
        RegionParser.Region region = RegionParser.parse(rdnmadr);

        return TraditionalMarket.builder()
                .name(mrktNm)
                .address(rdnmadr)
                .lat(latValue)
                .lng(lngValue)
                .marketType(mrktType)
                .phoneNumber(phoneNumber)
                .homepageUrl(homepageUrl)
                .establishYear(parseYearOrNull(estblYear))
                .sido(region.sido())
                .sigungu(region.sigungu())
                .build();
    }

    /** "미상", "2020년" 같은 비정형 값은 null 반환. */
    public static Integer parseYearOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
