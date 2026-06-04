package com.chunbaetour.domain.festival.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiFestivalItem(
        @JsonProperty("contentid")      String contentid,
        @JsonProperty("title")          String title,
        @JsonProperty("addr1")          String addr1,
        @JsonProperty("eventstartdate") String eventstartdate,
        @JsonProperty("eventenddate")   String eventenddate,
        @JsonProperty("firstimage")     String firstimage,
        @JsonProperty("lDongRegnCd")    String lDongRegnCd
) {}
