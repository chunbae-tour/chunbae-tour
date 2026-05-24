package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record QrPayCreateRequest(
        @NotNull Long shopId,
        @NotEmpty @Valid List<QrPayItemRequest> menuItems
) {}
