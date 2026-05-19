package com.chunbaetour.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\W_]).{8,}$")
        String password,

        @NotBlank
        @Size(min = 2, max = 20)
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]{2,20}$")
        String nickname
) {
}
