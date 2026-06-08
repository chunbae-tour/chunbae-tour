package com.chunbaetour.domain.place.dto.response;

public record DirectionResponse(
    String provider,
    String redirectUrl
) {
    public static DirectionResponse of(String provider, String redirectUrl) {
        return new DirectionResponse(provider, redirectUrl);
    }
}
