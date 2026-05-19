package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;

public record SignupResponse(
        Long userId,
        String email,
        String nickname,
        Role role,
        AccountStatus status
) {

    public static SignupResponse from(Account account) {
        return new SignupResponse(
                account.getId(),
                account.getEmail(),
                account.getNickname(),
                account.getRole(),
                account.getStatus()
        );
    }
}
