package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.Size;

public record FaqUpdateRequest(
        @Size(max = 500) String question,
        @Size(max = 5000) String answer,
        @Size(max = 50) String category
) {}
