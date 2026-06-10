package com.chunbaetour.domain.translation.service;

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
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final GoogleTranslationClient googleTranslationClient;
    private final TranslationErrorLogWriter errorLogWriter;
    private final TranslationCacheRepository translationCacheRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired @Lazy
    private TranslationService self;

    // 텍스트를 targetLanguage로 번역. sourceType이 정적 도메인이면 DB+Redis 캐시 경유, 동적 도메인이면 매번 Google API 호출
    public TranslationResponse translate(String content, LanguageCode targetLanguage, TranslationSourceType sourceType) {
        if (!sourceType.isCacheable()) {
            return translateViaApi(content, targetLanguage);
        }
        return self.translateCached(hash(content), content, targetLanguage);
    }

    // Redis(@Cacheable, TTL 24h) 적재 → miss 시 DB(translation_cache) 조회 → DB도 miss면 Google API 호출 후 DB 저장
    @Cacheable(value = "translation", key = "#contentHash + '_' + #targetLanguage")
    public TranslationResponse translateCached(String contentHash, String content, LanguageCode targetLanguage) {
        return translationCacheRepository.findByContentHashAndTargetLanguage(contentHash, targetLanguage)
                .map(cache -> new TranslationResponse(cache.getTranslatedContent(), targetLanguage))
                .orElseGet(() -> {
                    TranslationResponse response = translateViaApi(content, targetLanguage);
                    self.saveCacheEntry(contentHash, targetLanguage, response.translatedContent());
                    return response;
                });
    }

    // 신규 번역 결과 DB 저장 — 동시 요청으로 같은 (contentHash, targetLanguage) 먼저 저장된 경우 무시
    @Transactional
    public void saveCacheEntry(String contentHash, LanguageCode targetLanguage, String translatedContent) {
        try {
            translationCacheRepository.saveAndFlush(TranslationCache.builder()
                    .contentHash(contentHash)
                    .targetLanguage(targetLanguage)
                    .translatedContent(translatedContent)
                    .build());
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
        }
    }

    private TranslationResponse translateViaApi(String content, LanguageCode targetLanguage) {
        try {
            String translated = googleTranslationClient.translate(content, targetLanguage);
            return new TranslationResponse(translated, targetLanguage);
        } catch (TranslationClientException e) {
            try {
                errorLogWriter.save(e);
            } catch (Exception logEx) {
                log.error("CommonErrorLog 저장 실패. 번역 실패 로그가 누락됩니다.", logEx);
            }
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // NFC 정규화 + trim 후 해시 — 동일 문자열의 다른 유니코드 표현(NFC/NFD)이 다른 해시로 캐시 미스되는 것 방지
    private static String hash(String content) {
        try {
            String normalized = Normalizer.normalize(content, Normalizer.Form.NFC).trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
