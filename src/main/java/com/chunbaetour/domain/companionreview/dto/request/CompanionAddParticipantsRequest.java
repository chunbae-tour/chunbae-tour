package com.chunbaetour.domain.companionreview.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CompanionAddParticipantsRequest(
        @NotEmpty(message = "최소 1명 이상의 사용자를 선택해야 합니다.") List<@NotNull Long> userIds
) {}
