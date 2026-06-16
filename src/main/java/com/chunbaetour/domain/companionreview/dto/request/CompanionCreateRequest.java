package com.chunbaetour.domain.companionreview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CompanionCreateRequest(
        // 방장 제외 나머지 참여자 ID. 서비스에서 방장 자동 포함 — 최종 allParticipantIds.size() < 2이면 CR_016
        @Schema(description = "방장 외 동행 참여자 userId 목록 (방장은 자동 포함, 최소 1명 이상 필요)") @NotNull List<@NotNull Long> participantUserIds,
        @Schema(description = "동행 여행 시작일") @NotNull LocalDate tripStartDate,
        @Schema(description = "동행 여행 종료일 (tripStartDate 이상)") @NotNull LocalDate tripEndDate
) {}
