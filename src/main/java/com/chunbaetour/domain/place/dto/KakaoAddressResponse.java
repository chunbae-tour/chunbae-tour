package com.chunbaetour.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 주소 검색 API 응답 DTO.
 * GET https://dapi.kakao.com/v2/local/search/address.json
 */
public record KakaoAddressResponse(
        List<Document> documents,
        Meta meta
) {
    public record Document(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("address_type") String addressType,
            @JsonProperty("x") String x,  // 경도(lng)
            @JsonProperty("y") String y,  // 위도(lat)
            Address address,
            @JsonProperty("road_address") RoadAddress roadAddress
    ) {}

    public record Address(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName,
            @JsonProperty("road_name") String roadName,
            @JsonProperty("x") String x,
            @JsonProperty("y") String y
    ) {}

    public record RoadAddress(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName,
            @JsonProperty("road_name") String roadName,
            @JsonProperty("building_name") String buildingName,
            @JsonProperty("x") String x,
            @JsonProperty("y") String y
    ) {}

    public record Meta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("pageable_count") int pageableCount,
            @JsonProperty("is_end") boolean isEnd
    ) {}
}
