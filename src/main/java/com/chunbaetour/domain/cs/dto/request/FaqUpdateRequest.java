package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.Size;

public record FaqUpdateRequest(
        @Size(max = 500) String question,
        String answer,
        @Size(max = 50) String category
) {}
