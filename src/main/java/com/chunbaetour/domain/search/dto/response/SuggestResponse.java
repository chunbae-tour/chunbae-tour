package com.chunbaetour.domain.search.dto.response;

public record SuggestResponse(
        String keyword,
        SuggestSource source
) {
    public enum SuggestSource {
        DB,
        KAKAO,
        REDIS
    }
}
