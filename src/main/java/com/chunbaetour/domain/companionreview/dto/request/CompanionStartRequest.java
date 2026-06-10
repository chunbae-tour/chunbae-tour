package com.chunbaetour.domain.companionreview.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CompanionStartRequest(
        @NotEmpty List<@NotNull Long> participantUserIds
) {}
