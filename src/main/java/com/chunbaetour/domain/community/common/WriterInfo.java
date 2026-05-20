package com.chunbaetour.domain.community.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WriterInfo(
        Long userId,
        String nickname,
        String profileImageUrl,
        Double companionScore
) {
}
