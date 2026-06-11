package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.translation.type.LanguageCode;

public record FaqTranslationResponse(
        Long faqId,
        String question,
        String answer,
        LanguageCode targetLanguage
) {}
