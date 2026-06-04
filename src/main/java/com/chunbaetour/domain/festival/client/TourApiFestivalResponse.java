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

    // totalCount/numOfRows/pageNo 는 이 API에서 String으로 반환됨
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JsonProperty("items")      List<TourApiFestivalItem> items,
            @JsonProperty("numOfRows")  String numOfRows,
            @JsonProperty("pageNo")     String pageNo,
            @JsonProperty("totalCount") String totalCount
    ) {
        public List<TourApiFestivalItem> itemList() {
            return items != null ? items : List.of();
        }

        public int totalCountInt() {
            try { return Integer.parseInt(totalCount); } catch (Exception e) { return 0; }
        }
    }
}
