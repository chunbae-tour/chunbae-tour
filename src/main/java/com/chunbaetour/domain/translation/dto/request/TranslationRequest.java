package com.chunbaetour.domain.translation.dto.request;

import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TranslationRequest(
        // max=1000: Message.content 최대 길이와 일치 — 채팅 메시지 번역 용도
        @NotBlank @Size(max = 1000) String content,
        @NotNull LanguageCode targetLanguage,
        // 캐시 적용 도메인(FAQ)은 본 endpoint에서 거부 — 전용 entity-ID 기반 endpoint 사용
        @NotNull TranslationSourceType sourceType
) {}
