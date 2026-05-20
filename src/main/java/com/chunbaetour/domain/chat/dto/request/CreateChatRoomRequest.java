package com.chunbaetour.domain.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateChatRoomRequest(
        @NotNull Long postId,
        @NotBlank String title,
        String description,
        int maxMembers
) {}
