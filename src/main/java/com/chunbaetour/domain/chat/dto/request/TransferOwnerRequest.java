package com.chunbaetour.domain.chat.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransferOwnerRequest(
        @NotNull @Min(1) Long newOwnerId
) {}
