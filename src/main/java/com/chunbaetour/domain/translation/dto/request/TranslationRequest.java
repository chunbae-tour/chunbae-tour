package com.chunbaetour.domain.translation.dto.request;

import com.chunbaetour.domain.translation.type.LanguageCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TranslationRequest(
        @NotBlank @Size(max = 5000) String content,
        @NotNull LanguageCode targetLanguage
) {}
