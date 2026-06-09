package com.chunbaetour.domain.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** KorService2 detailIntro2(소개 상세, contentTypeId=12) 응답 — usetime(이용시간)·restdate(쉬는날)만 사용. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailIntroResponse(
        @JsonProperty("response") Response response
) {

    public String resultCode() {
        return (response != null && response.header() != null) ? response.header().resultCode() : null;
    }

    private Item firstItem() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return null;
        }
        List<Item> items = response.body().items().item();
        return (items == null || items.isEmpty()) ? null : items.get(0);
    }

    public String usetime() {
        Item item = firstItem();
        return item == null ? null : item.usetime();
    }

    public String restdate() {
        Item item = firstItem();
        return item == null ? null : item.restdate();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("header") Header header, @JsonProperty("body") Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(@JsonProperty("resultCode") String resultCode,
                         @JsonProperty("resultMsg") String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(@JsonProperty("items") Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(@JsonProperty("item") List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(@JsonProperty("usetime") String usetime,
                       @JsonProperty("restdate") String restdate,
                       @JsonProperty("parking") String parking) {}
}
