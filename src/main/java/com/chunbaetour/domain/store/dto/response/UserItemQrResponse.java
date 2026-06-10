package com.chunbaetour.domain.store.dto.response;

import java.time.Instant;

public record UserItemQrResponse(
        String token,
        Instant expiresAt
) {
}
