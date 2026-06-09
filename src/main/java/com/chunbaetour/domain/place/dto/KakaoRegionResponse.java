package com.chunbaetour.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 좌표 -> 행정구역 변환(coord2regioncode) API 응답용 DTO
 */
public record KakaoRegionResponse(
        Meta meta,
        List<Document> documents
) {
    public record Meta(
            @JsonProperty("total_count")
            Integer totalCount
    ) {}

    public record Document(
            @JsonProperty("region_type")
            String regionType,
            @JsonProperty("address_name")
            String addressName,
            @JsonProperty("region_1depth_name")
            String region1depthName,
            @JsonProperty("region_2depth_name")
            String region2depthName,
            @JsonProperty("region_3depth_name")
            String region3depthName,
            @JsonProperty("region_4depth_name")
            String region4depthName
    ) {}
}
