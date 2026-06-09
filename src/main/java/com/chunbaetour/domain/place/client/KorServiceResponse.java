package com.chunbaetour.domain.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 한국관광공사 KorService2 공통 응답 래퍼.
 *
 * <p>축제(표준데이터)와 구조가 다르다:
 * <ul>
 *   <li>성공 코드가 {@code "0000"} (표준데이터는 {@code "00"})</li>
 *   <li>{@code body.items}가 객체이며 그 안에 {@code item} 배열을 둔다 (표준데이터는 items가 곧 배열)</li>
 *   <li>numOfRows/pageNo/totalCount가 숫자형 (표준데이터는 문자열)</li>
 * </ul>
 *
 * <p>데이터가 없을 때 {@code "items": ""}(빈 문자열)로 오는 케이스는 RestClient의 관용 ObjectMapper
 * (ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)가 null로 역직렬화 → {@link Body#itemList()}가 빈 리스트 반환.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KorServiceResponse(
        @JsonProperty("response") Response response
) {

    public static final String SUCCESS_CODE = "0000";

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
            @JsonProperty("numOfRows")  Integer numOfRows,
            @JsonProperty("pageNo")     Integer pageNo,
            @JsonProperty("totalCount") Integer totalCount
    ) {
        public List<TourApiPlaceItem> itemList() {
            return (items != null && items.item() != null) ? items.item() : List.of();
        }

        public int totalCountValue() {
            return totalCount != null ? totalCount : 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<TourApiPlaceItem> item
    ) {}
}
