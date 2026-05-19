package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenPair;

public record LoginResponse(String accessToken, String refreshToken, Role role) {

    public static LoginResponse from(TokenPair pair) {
        return new LoginResponse(pair.accessToken(), pair.refreshToken(), pair.role());
    }
}
