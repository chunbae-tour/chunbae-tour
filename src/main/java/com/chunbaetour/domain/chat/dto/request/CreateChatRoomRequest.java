package com.chunbaetour.domain.chat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChatRoomRequest(
        @NotNull Long postId,
        @NotBlank @Size(max = 50) String title,
        String description,
        @Min(2) @Max(50) int maxMembers
) {}
