package com.chunbaetour.domain.companionreview.dto.response;

import java.util.List;

public record CompanionAddParticipantsResponse(
        Long companionId,
        List<Long> addedUserIds
) {}
