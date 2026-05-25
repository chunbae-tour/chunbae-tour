package com.chunbaetour.domain.chat.dto.request;

import jakarta.validation.constraints.Size;

public record CreateJoinRequestRequest(
        @Size(max = 500) String message
) {}
