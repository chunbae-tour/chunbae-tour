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
        // 정적 도메인(FESTIVAL/ATTRACTION/MARKET/FAQ/NOTICE)이면 DB+Redis 캐시 경유
        @NotNull TranslationSourceType sourceType
) {}
