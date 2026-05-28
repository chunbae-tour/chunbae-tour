package com.chunbaetour.domain.translation.dto.response;

import com.chunbaetour.domain.translation.type.LanguageCode;

public record TranslationResponse(
        String translatedContent,
        LanguageCode targetLanguage
) {}
