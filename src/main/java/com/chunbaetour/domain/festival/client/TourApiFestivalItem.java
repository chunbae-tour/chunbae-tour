package com.chunbaetour.domain.festival.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiFestivalItem(
        @JsonProperty("insttCode")      String insttCode,
        @JsonProperty("fstvlNm")        String fstvlNm,
        @JsonProperty("opar")           String opar,
        @JsonProperty("fstvlStartDate") String fstvlStartDate,
        @JsonProperty("fstvlEndDate")   String fstvlEndDate,
        @JsonProperty("fstvlCo")        String fstvlCo,
        @JsonProperty("mnnstNm")        String mnnstNm,
        @JsonProperty("rdnmadr")        String rdnmadr,
        @JsonProperty("homepageUrl")    String homepageUrl,
        @JsonProperty("phoneNumber")    String phoneNumber,
        @JsonProperty("latitude")       String latitude,
        @JsonProperty("longitude")      String longitude,
        @JsonProperty("insttNm")        String insttNm
) {}
