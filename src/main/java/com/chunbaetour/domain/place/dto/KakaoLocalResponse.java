package com.chunbaetour.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoLocalResponse(List<Document> documents) {
    public record Document(
            @JsonProperty("road_address") RoadAddress roadAddress,
            Address address
    ) {}
    public record RoadAddress(@JsonProperty("address_name") String addressName) {}
    public record Address(@JsonProperty("address_name") String addressName) {}
}
