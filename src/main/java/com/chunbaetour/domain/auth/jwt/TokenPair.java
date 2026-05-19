package com.chunbaetour.domain.auth.jwt;

import com.chunbaetour.domain.auth.Role;

public record TokenPair(String accessToken, String refreshToken, String refreshTokenId, Role role) {
}
