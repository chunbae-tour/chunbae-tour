package com.chunbaetour.domain.place.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoCategoryResponse(List<Document> documents, Meta meta) {
    public record Meta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("pageable_count") int pageableCount,
            @JsonProperty("is_end") boolean isEnd
    ) {}

    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl,
            String distance
    ) {}
}
