package com.chunbaetour.domain.market.dto.response;

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
     * estblYear는 String → Integer 변환.
     */
    public TraditionalMarket toEntity() {
        BigDecimal latValue = latitude != null && !latitude.isBlank()
            ? new BigDecimal(latitude)
            : null;
        BigDecimal lngValue = longitude != null && !longitude.isBlank()
            ? new BigDecimal(longitude)
            : null;
        Integer yearValue = estblYear != null && !estblYear.isBlank()
            ? Integer.parseInt(estblYear)
            : null;

        return TraditionalMarket.builder()
                .name(mrktNm)
                .address(rdnmadr)
                .lat(latValue)
                .lng(lngValue)
                .marketType(mrktType)
                .phoneNumber(phoneNumber)
                .homepageUrl(homepageUrl)
                .establishYear(yearValue)
                .build();
    }
}
