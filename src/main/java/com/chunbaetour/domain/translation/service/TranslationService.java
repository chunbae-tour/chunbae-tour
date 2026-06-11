package com.chunbaetour.domain.translation.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    // SHA-256 MessageDigest는 스레드 세이프하지 않고 getInstance() 매 호출 시 JCA provider 조회 비용 발생 — 스레드별 재사용
    private static final ThreadLocal<MessageDigest> SHA_256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    });

    private final GoogleTranslationClient googleTranslationClient;
    private final TranslationErrorLogWriter errorLogWriter;
    private final TranslationCacheService translationCacheService;

    // 텍스트를 targetLanguage로 번역. sourceType이 정적 도메인이면 DB+Redis 캐시 경유, 동적 도메인이면 매번 Google API 호출
    public TranslationResponse translate(String content, LanguageCode targetLanguage, TranslationSourceType sourceType) {
        if (!sourceType.isCacheable()) {
            return translateViaApi(content, targetLanguage);
        }
        return translationCacheService.translateCached(
                hash(content), targetLanguage, () -> translateViaApi(content, targetLanguage));
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
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFC).trim();
        MessageDigest digest = SHA_256_DIGEST.get();
        digest.reset();
        byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
