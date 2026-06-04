package com.chunbaetour.domain.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터포털 API 전체 응답 구조.
 * header(resultCode)와 body(실제 데이터)를 포함.
 */
@Getter
@NoArgsConstructor
public class PublicDataApiWrapper {

    @JsonProperty("response")
    public ApiResponse response;

    @Getter
    @NoArgsConstructor
    public static class ApiResponse {
        @JsonProperty("header")
        public Header header;

        @JsonProperty("body")
        public MarketApiResponse body;
    }

    @Getter
    @NoArgsConstructor
    public static class Header {
        @JsonProperty("resultCode")
        public String resultCode;

        @JsonProperty("resultMsg")
        public String resultMsg;
    }
}
