package com.chunbaetour.domain.companion.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanionReviewCreateRequest(
        @NotNull Long chatRoomId,
        @NotNull Long targetUserId,
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 1000) String content
) {}
