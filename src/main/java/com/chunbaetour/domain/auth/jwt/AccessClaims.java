package com.chunbaetour.domain.auth.jwt;

import com.chunbaetour.domain.auth.Role;

public record AccessClaims(long userId, Role role, String email, String tokenId) {
}
