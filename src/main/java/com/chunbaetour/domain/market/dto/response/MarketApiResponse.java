package com.chunbaetour.domain.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터포털 전통시장 API 응답 (최상위).
 * response 객체 하위의 body 부분을 파싱.
 */
@Getter
@NoArgsConstructor
public class MarketApiResponse {

    @JsonProperty("pageNo")
    public Integer pageNo;

    @JsonProperty("numOfRows")
    public Integer numOfRows;

    @JsonProperty("totalCount")
    public Integer totalCount;

    @JsonProperty("items")
    public List<MarketApiItem> items;
}
