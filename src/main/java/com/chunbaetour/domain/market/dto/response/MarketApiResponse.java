package com.chunbaetour.domain.market.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketApiResponse(
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
            @JsonProperty("items")      List<MarketApiItem> items,
            @JsonProperty("numOfRows")  String numOfRows,
            @JsonProperty("pageNo")     String pageNo,
            @JsonProperty("totalCount") String totalCount
    ) {
        private static final Logger log = LoggerFactory.getLogger(Body.class);

        public List<MarketApiItem> itemList() {
            return items != null ? items : List.of();
        }

        public int totalCountInt() {
            if (totalCount == null || totalCount.isBlank()) {
                throw new IllegalStateException("[MarketSync] totalCount 누락 — 수집 중단");
            }
            try {
                return Integer.parseInt(totalCount.trim());
            } catch (NumberFormatException e) {
                log.error("[MarketSync] totalCount 파싱 실패 — 수집 중단: '{}'", totalCount);
                throw new IllegalStateException("[MarketSync] totalCount 파싱 실패: " + totalCount, e);
            }
        }

    }
}
