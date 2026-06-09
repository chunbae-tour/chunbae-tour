package com.chunbaetour.domain.companionreview.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CompanionAddParticipantsRequest(
        @NotNull List<@NotNull Long> userIds
) {}
