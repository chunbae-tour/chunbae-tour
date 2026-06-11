package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.entity.TranslationCache;
import com.chunbaetour.domain.translation.repository.TranslationCacheRepository;
import com.chunbaetour.domain.translation.type.LanguageCode;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TranslationCacheServiceTest {

    @Mock
    private TranslationCacheRepository translationCacheRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TranslationCacheService translationCacheService;

    @BeforeEach
    void setUp() {
        // entityManager는 @PersistenceContext 필드 — 생성자 주입 대상이 아니라 InjectMocks가 채우지 못해 수동 주입
        ReflectionTestUtils.setField(translationCacheService, "entityManager", entityManager);
    }

    // DB 캐시 hit — apiCall 미실행, DB 저장도 없음
    @Test
    @SuppressWarnings("unchecked")
    void translateCached_dbHit_returnsCachedWithoutInvokingApiCall() {
        TranslationCache cached = TranslationCache.builder()
                .contentHash("hash")
                .targetLanguage(LanguageCode.EN)
                .translatedContent("What are the business hours?")
                .build();
        given(translationCacheRepository.findByContentHashAndTargetLanguage("hash", LanguageCode.EN))
                .willReturn(Optional.of(cached));
        Supplier<TranslationResponse> apiCall = mock(Supplier.class);

        TranslationResponse result = translationCacheService.translateCached("hash", LanguageCode.EN, apiCall);

        assertThat(result.translatedContent()).isEqualTo("What are the business hours?");
        verify(apiCall, never()).get();
        verify(translationCacheRepository, never()).saveAndFlush(any());
    }

    // DB 캐시 miss — apiCall 실행 후 결과 DB 저장
    @Test
    void translateCached_dbMiss_callsApiCallAndSavesCacheEntry() {
        given(translationCacheRepository.findByContentHashAndTargetLanguage("hash", LanguageCode.EN))
                .willReturn(Optional.empty());
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        Supplier<TranslationResponse> apiCall = () -> new TranslationResponse("Hello", LanguageCode.EN);

        TranslationResponse result = translationCacheService.translateCached("hash", LanguageCode.EN, apiCall);

        assertThat(result.translatedContent()).isEqualTo("Hello");
        ArgumentCaptor<TranslationCache> captor = ArgumentCaptor.forClass(TranslationCache.class);
        verify(translationCacheRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContentHash()).isEqualTo("hash");
        assertThat(captor.getValue().getTargetLanguage()).isEqualTo(LanguageCode.EN);
        assertThat(captor.getValue().getTranslatedContent()).isEqualTo("Hello");
    }

    // 동시 요청 race — saveAndFlush 시 DataIntegrityViolationException 발생해도 흡수, entityManager.clear() 호출, 응답은 정상 반환
    @Test
    void translateCached_saveCacheEntryDuplicateKeyRace_swallowsExceptionAndClearsContext() {
        given(translationCacheRepository.findByContentHashAndTargetLanguage("hash", LanguageCode.EN))
                .willReturn(Optional.empty());
        given(translationCacheRepository.saveAndFlush(any(TranslationCache.class)))
                .willThrow(new DataIntegrityViolationException("uq_translation_cache_hash_lang"));
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        Supplier<TranslationResponse> apiCall = () -> new TranslationResponse("Hello", LanguageCode.EN);

        TranslationResponse result = translationCacheService.translateCached("hash", LanguageCode.EN, apiCall);

        assertThat(result.translatedContent()).isEqualTo("Hello");
        verify(entityManager).clear();
    }

    // 캐시 저장 중 예기치 못한 예외(커넥션 끊김 등) 발생해도 흡수 — 이미 받은 번역 결과는 정상 반환
    @Test
    void translateCached_saveCacheEntryUnexpectedException_stillReturnsApiResponse() {
        given(translationCacheRepository.findByContentHashAndTargetLanguage("hash", LanguageCode.EN))
                .willReturn(Optional.empty());
        given(translationCacheRepository.saveAndFlush(any(TranslationCache.class)))
                .willThrow(new RuntimeException("커넥션 끊김"));
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        Supplier<TranslationResponse> apiCall = () -> new TranslationResponse("Hello", LanguageCode.EN);

        TranslationResponse result = translationCacheService.translateCached("hash", LanguageCode.EN, apiCall);

        assertThat(result.translatedContent()).isEqualTo("Hello");
    }
}
