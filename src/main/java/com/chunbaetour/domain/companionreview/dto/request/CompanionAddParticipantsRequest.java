package com.chunbaetour.domain.companionreview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CompanionAddParticipantsRequest(
        @Schema(description = "추가할 채팅방 ACTIVE 멤버의 userId 목록 (1~50명)")
        @NotEmpty(message = "최소 1명 이상의 사용자를 선택해야 합니다.")
        @Size(max = 50, message = "한 번에 추가할 수 있는 인원은 최대 50명입니다.")
        List<@NotNull @Positive Long> userIds
) {}
