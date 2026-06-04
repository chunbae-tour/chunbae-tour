package com.chunbaetour.domain.festival.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiFestivalResponse(
        @JsonProperty("response") Response response
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            @JsonProperty("header") Header header,
            @JsonProperty("body")   Body body
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            @JsonProperty("resultCode") String resultCode,
            @JsonProperty("resultMsg")  String resultMsg
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JsonProperty("items")      Items items,
            @JsonProperty("numOfRows")  int numOfRows,
            @JsonProperty("pageNo")     int pageNo,
            @JsonProperty("totalCount") int totalCount
    ) {
        public List<TourApiFestivalItem> itemList() {
            if (items == null || items.item() == null) return List.of();
            return items.item();
        }
    }

    // "items": "" (결과 없음) → null로 역직렬화 (ObjectMapper ACCEPT_EMPTY_STRING_AS_NULL_OBJECT 설정 필요)
    // "items": {"item": {...}} (결과 1건) → ACCEPT_SINGLE_VALUE_AS_ARRAY로 처리
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<TourApiFestivalItem> item
    ) {}
}
