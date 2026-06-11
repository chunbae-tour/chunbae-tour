package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private GoogleTranslationClient googleTranslationClient;

    @Mock
    private TranslationErrorLogWriter errorLogWriter;

    @Mock
    private TranslationCacheService translationCacheService;

    @InjectMocks
    private TranslationService translationService;

    // 동적 도메인(CHAT) 번역 성공 — 결과 반환, ErrorLog 저장 없음, 캐시 빈 미사용
    @Test
    void translate_dynamicSourceType_success() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN)).willReturn("Hello");

        TranslationResponse result = translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT);

        assertThat(result.translatedContent()).isEqualTo("Hello");
        assertThat(result.targetLanguage()).isEqualTo(LanguageCode.EN);
        verify(errorLogWriter, never()).save(any());
        verify(translationCacheService, never()).translateCached(any(), any(), any());
    }

    // 번역 실패 — errorLogWriter.save() 호출 + EXTERNAL_SERVICE_ERROR throw
    @Test
    void translate_clientException_savesErrorLogAndThrows() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN))
                .willThrow(new TranslationClientException("Google Translation API 호출 실패"));

        assertThatThrownBy(() -> translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));

        verify(errorLogWriter).save(any(TranslationClientException.class));
    }

    // ErrorLog 저장 실패(커밋 실패 등) — 바깥 try/catch 흡수 후 원본 EXTERNAL_SERVICE_ERROR 전파
    @Test
    void translate_errorLogSaveFails_stillThrowsExternalServiceError() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN))
                .willThrow(new TranslationClientException("호출 실패"));
        willThrow(new RuntimeException("트랜잭션 커밋 실패")).given(errorLogWriter).save(any());

        assertThatThrownBy(() -> translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
    }

    // 정적 도메인(FAQ) — translationCacheService.translateCached()에 contentHash + apiCall을 위임, 결과 그대로 반환
    @Test
    void translate_staticSourceType_delegatesToCacheService() {
        given(translationCacheService.translateCached(anyString(), eq(LanguageCode.EN), any()))
                .willReturn(new TranslationResponse("What are the business hours?", LanguageCode.EN));

        TranslationResponse result =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        assertThat(result.translatedContent()).isEqualTo("What are the business hours?");
        verify(googleTranslationClient, never()).translate(any(), any());
    }

    // 정적 도메인(FAQ) — translateCached에 전달된 apiCall 실행 시 Google API 호출
    @Test
    void translate_staticSourceType_apiCallSupplier_invokesGoogleTranslationClient() {
        given(googleTranslationClient.translate("운영시간이 어떻게 되나요?", LanguageCode.EN))
                .willReturn("What are the business hours?");
        given(translationCacheService.translateCached(anyString(), eq(LanguageCode.EN), any()))
                .willAnswer(invocation -> {
                    Supplier<TranslationResponse> apiCall = invocation.getArgument(2);
                    return apiCall.get();
                });

        TranslationResponse result =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        assertThat(result.translatedContent()).isEqualTo("What are the business hours?");
        verify(googleTranslationClient).translate("운영시간이 어떻게 되나요?", LanguageCode.EN);
    }

    // 동일 content는 호출마다 동일한 contentHash 생성 — ThreadLocal<MessageDigest> 재사용 시에도 일관성 유지
    @Test
    void translate_staticSourceType_sameContent_producesSameContentHash() {
        given(translationCacheService.translateCached(anyString(), eq(LanguageCode.EN), any()))
                .willReturn(new TranslationResponse("cached", LanguageCode.EN));

        translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);
        translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(translationCacheService, times(2)).translateCached(hashCaptor.capture(), eq(LanguageCode.EN), any());
        assertThat(hashCaptor.getAllValues().get(0)).isEqualTo(hashCaptor.getAllValues().get(1));
    }
}
