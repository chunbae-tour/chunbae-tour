package com.chunbaetour.domain.community.companion.service;

import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;

public record ValidatedCompanionTarget(
        CompanionTargetType targetType,
        Long targetId,
        String targetName
) {
}
