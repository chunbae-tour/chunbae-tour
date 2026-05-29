package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.type.LanguageCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private GoogleTranslationClient googleTranslationClient;

    @Mock
    private TranslationErrorLogWriter errorLogWriter;

    @InjectMocks
    private TranslationService translationService;

    // 번역 성공 — 결과 반환, ErrorLog 저장 없음
    @Test
    void translate_success() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN)).willReturn("Hello");

        TranslationResponse result = translationService.translate("안녕", LanguageCode.EN);

        assertThat(result.translatedContent()).isEqualTo("Hello");
        assertThat(result.targetLanguage()).isEqualTo(LanguageCode.EN);
        verify(errorLogWriter, never()).save(any());
    }

    // 번역 실패 — errorLogWriter.save() 호출 + EXTERNAL_SERVICE_ERROR throw
    @Test
    void translate_clientException_savesErrorLogAndThrows() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN))
                .willThrow(new TranslationClientException("Google Translation API 호출 실패"));

        assertThatThrownBy(() -> translationService.translate("안녕", LanguageCode.EN))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));

        verify(errorLogWriter).save(any(TranslationClientException.class));
    }

    // ErrorLog 저장 실패 — 원본 EXTERNAL_SERVICE_ERROR 그대로 전파 (저장 실패가 응답 덮지 않음)
    @Test
    void translate_errorLogSaveFails_stillThrowsExternalServiceError() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN))
                .willThrow(new TranslationClientException("호출 실패"));
        willThrow(new RuntimeException("DB 장애")).given(errorLogWriter).save(any());

        assertThatThrownBy(() -> translationService.translate("안녕", LanguageCode.EN))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
    }
}
