package com.chunbaetour.domain.translation.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.type.LanguageCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final GoogleTranslationClient googleTranslationClient;
    private final TranslationErrorLogWriter errorLogWriter;

    // 텍스트를 targetLanguage로 번역. 외부 API 실패 시 CommonErrorLog 기록 후 EXTERNAL_SERVICE_ERROR
    public TranslationResponse translate(String content, LanguageCode targetLanguage) {
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
}
