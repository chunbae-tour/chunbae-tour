package com.chunbaetour.domain.companion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CompanionAddParticipantsResponse(
        @Schema(description = "동행 ID") Long companionId,
        @Schema(description = "이번 요청으로 추가된 userId 목록 (중복 제거됨)") List<Long> addedUserIds
) {}
