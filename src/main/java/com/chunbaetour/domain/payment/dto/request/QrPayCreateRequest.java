package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QrPayCreateRequest(
        @NotNull Long shopId,
        @NotEmpty @Size(max = 50) @Valid List<QrPayItemRequest> menuItems
) {}
