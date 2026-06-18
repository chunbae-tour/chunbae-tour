package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompanionPostUpdateRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 5000) String content,
        // 대상 변경 시 셋을 함께 보낸다(엔티티 update에서 부분 입력 검증). 미변경 시 모두 null.
        CompanionTargetType targetType,
        @Positive Long targetId,
        @Size(min = 1, max = 100) String targetName,
        @Size(max = 50) String region,
        @FutureOrPresent LocalDate meetingDate,
        @Min(2) @Max(50) Integer maxMembers
) {
}
