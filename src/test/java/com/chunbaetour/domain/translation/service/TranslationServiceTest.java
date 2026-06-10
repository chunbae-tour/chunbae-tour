package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.entity.TranslationCache;
import com.chunbaetour.domain.translation.repository.TranslationCacheRepository;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private GoogleTranslationClient googleTranslationClient;

    @Mock
    private TranslationErrorLogWriter errorLogWriter;

    @Mock
    private TranslationCacheRepository translationCacheRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        // self는 @Cacheable 프록시 없이 동일 인스턴스 직접 호출 — 캐시 적용은 통합 테스트에서 검증
        ReflectionTestUtils.setField(translationService, "self", translationService);
        // entityManager는 @PersistenceContext 필드 — 생성자 주입 대상이 아니라 InjectMocks가 채우지 못해 수동 주입
        ReflectionTestUtils.setField(translationService, "entityManager", entityManager);
    }

    // 동적 도메인(CHAT) 번역 성공 — 결과 반환, ErrorLog 저장 없음, 캐시 미사용
    @Test
    void translate_dynamicSourceType_success() {
        given(googleTranslationClient.translate("안녕", LanguageCode.EN)).willReturn("Hello");

        TranslationResponse result = translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT);

        assertThat(result.translatedContent()).isEqualTo("Hello");
        assertThat(result.targetLanguage()).isEqualTo(LanguageCode.EN);
        verify(errorLogWriter, never()).save(any());
        verify(translationCacheRepository, never()).findByContentHashAndTargetLanguage(any(), any());
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

    // 정적 도메인(FAQ) + DB 캐시 hit — Google API 미호출, DB 저장 결과 그대로 반환
    @Test
    void translate_staticSourceType_dbHit_returnsCachedWithoutApiCall() {
        TranslationCache cached = TranslationCache.builder()
                .contentHash("hash")
                .targetLanguage(LanguageCode.EN)
                .translatedContent("What are the business hours?")
                .build();
        given(translationCacheRepository.findByContentHashAndTargetLanguage(anyString(), eq(LanguageCode.EN)))
                .willReturn(Optional.of(cached));

        TranslationResponse result =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        assertThat(result.translatedContent()).isEqualTo("What are the business hours?");
        verify(googleTranslationClient, never()).translate(any(), any());
    }

    // 정적 도메인(FAQ) + DB 캐시 miss — Google API 호출 후 결과 DB 저장
    @Test
    void translate_staticSourceType_dbMiss_callsApiAndSavesCache() {
        given(translationCacheRepository.findByContentHashAndTargetLanguage(anyString(), eq(LanguageCode.EN)))
                .willReturn(Optional.empty());
        given(googleTranslationClient.translate("운영시간이 어떻게 되나요?", LanguageCode.EN))
                .willReturn("What are the business hours?");

        TranslationResponse result =
                translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ);

        assertThat(result.translatedContent()).isEqualTo("What are the business hours?");
        verify(translationCacheRepository).saveAndFlush(any(TranslationCache.class));
    }

    // 동시 요청 race — saveAndFlush 시 DataIntegrityViolationException 발생해도 흡수, entityManager.clear() 호출
    @Test
    void saveCacheEntry_duplicateKeyRace_swallowsExceptionAndClearsContext() {
        given(translationCacheRepository.saveAndFlush(any(TranslationCache.class)))
                .willThrow(new DataIntegrityViolationException("uq_translation_cache_hash_lang"));

        translationService.saveCacheEntry("hash", LanguageCode.EN, "translated");

        verify(entityManager).clear();
    }
}
