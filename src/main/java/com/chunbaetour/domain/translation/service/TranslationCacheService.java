package com.chunbaetour.domain.translation.service;

import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.entity.TranslationCache;
import com.chunbaetour.domain.translation.repository.TranslationCacheRepository;
import com.chunbaetour.domain.translation.type.LanguageCode;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 번역 결과 DB+Redis 캐시 전담 — TranslationService와 빈을 분리해 @Cacheable/REQUIRES_NEW가
// self-invocation 없이 정상적으로 프록시를 거치도록 함
@Slf4j
@Service
public class TranslationCacheService {

    private final TranslationCacheRepository translationCacheRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public TranslationCacheService(
            TranslationCacheRepository translationCacheRepository, PlatformTransactionManager transactionManager) {
        this.translationCacheRepository = translationCacheRepository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Redis(@Cacheable, TTL 24h) 적재 → miss 시 DB(translation_cache) 조회 → DB도 miss면 apiCall 실행 후 DB 저장
    @Cacheable(value = "translation", key = "#contentHash + '_' + #targetLanguage.name()")
    public TranslationResponse translateCached(
            String contentHash, LanguageCode targetLanguage, Supplier<TranslationResponse> apiCall) {
        return translationCacheRepository.findByContentHashAndTargetLanguage(contentHash, targetLanguage)
                .map(cache -> new TranslationResponse(cache.getTranslatedContent(), targetLanguage))
                .orElseGet(() -> {
                    TranslationResponse response = apiCall.get();
                    // 캐시 저장 실패해도 이미 받은 번역 결과는 그대로 반환 — 다음 요청에서 Google API 재호출될 뿐
                    try {
                        saveCacheEntry(contentHash, targetLanguage, response.translatedContent());
                    } catch (Exception e) {
                        log.error("번역 캐시 저장 실패. content_hash={}, targetLanguage={}", contentHash, targetLanguage, e);
                    }
                    return response;
                });
    }

    // 신규 번역 결과 DB 저장 — 별도 트랜잭션(REQUIRES_NEW)으로 호출부(예: FaqService) 영속성 컨텍스트와 분리.
    // 동시 요청으로 같은 (contentHash, targetLanguage) 먼저 저장된 경우 무시
    private void saveCacheEntry(String contentHash, LanguageCode targetLanguage, String translatedContent) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            try {
                translationCacheRepository.saveAndFlush(TranslationCache.builder()
                        .contentHash(contentHash)
                        .targetLanguage(targetLanguage)
                        .translatedContent(translatedContent)
                        .build());
            } catch (DataIntegrityViolationException e) {
                log.debug("동시 요청으로 캐시 항목 이미 저장됨. content_hash={}, targetLanguage={}", contentHash, targetLanguage);
            }
        });
    }
}
