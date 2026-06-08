package com.chunbaetour.domain.auth.jwt;

public record RefreshClaims(long userId, String tokenId) {
}
