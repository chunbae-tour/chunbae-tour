package com.chunbaetour.domain.festival.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private static final Logger log = LoggerFactory.getLogger(Body.class);

        public List<TourApiFestivalItem> itemList() {
            return items != null ? items : List.of();
        }

        public int totalCountInt() {
            if (totalCount == null || totalCount.isBlank()) {
                return Integer.MAX_VALUE;
            }
            try {
                return Integer.parseInt(totalCount);
            } catch (NumberFormatException e) {
                log.warn("totalCount 파싱 실패: '{}' — itemList 비어있을 때까지 페이징 진행", totalCount);
                return Integer.MAX_VALUE;
            }
        }
    }
}
