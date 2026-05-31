package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqCreateRequest(
        @NotBlank @Size(max = 500) String question,
        @NotBlank String answer,
        @NotBlank @Size(max = 50) String category
) {}
