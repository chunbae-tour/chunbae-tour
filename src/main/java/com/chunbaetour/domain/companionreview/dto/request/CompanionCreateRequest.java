package com.chunbaetour.domain.companionreview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CompanionCreateRequest(
        // 빈 리스트 허용 — 방장만으로도 동행 생성 가능, 서비스에서 방장을 자동 포함
        @NotNull List<@NotNull Long> participantUserIds,
        @Schema(description = "동행 여행 시작일") @NotNull LocalDate tripStartDate,
        @Schema(description = "동행 여행 종료일 (tripStartDate 이상)") @NotNull LocalDate tripEndDate
) {}
