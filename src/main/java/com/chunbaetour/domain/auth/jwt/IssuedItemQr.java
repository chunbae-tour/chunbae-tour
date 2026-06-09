package com.chunbaetour.domain.auth.jwt;

import java.time.Instant;

/** 발급된 아이템 QR 토큰과 만료시각 — 발급 시점에 계산된 expiresAt을 함께 반환해 호출부의 재파싱을 없앤다. */
public record IssuedItemQr(String token, Instant expiresAt) {
}
