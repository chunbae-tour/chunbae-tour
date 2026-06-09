package com.chunbaetour.domain.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** KorService2 detailCommon2(공통 상세) 응답 — overview(개요)만 사용. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailCommonResponse(
        @JsonProperty("response") Response response
) {

    public String resultCode() {
        return (response != null && response.header() != null) ? response.header().resultCode() : null;
    }

    /** 첫 아이템의 overview. 없으면 null. */
    public String overview() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return null;
        }
        List<Item> items = response.body().items().item();
        return (items == null || items.isEmpty()) ? null : items.get(0).overview();
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
    public record Item(@JsonProperty("overview") String overview,
                       @JsonProperty("homepage") String homepage) {}
}
