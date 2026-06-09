package com.chunbaetour.domain.auth.jwt;

import java.time.Instant;

public record ItemQrClaims(long userId, long itemId, Instant expiresAt) {
}
