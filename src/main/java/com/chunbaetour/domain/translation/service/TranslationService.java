package com.chunbaetour.domain.translation.service;

import com.chunbaetour.domain.common.entity.CommonErrorLog;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.repository.CommonErrorLogRepository;
import com.chunbaetour.domain.common.type.CommonErrorDomain;
import com.chunbaetour.domain.common.type.CommonErrorType;
import com.chunbaetour.domain.translation.client.GoogleTranslationClient;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.type.LanguageCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private static final String EXTERNAL_PROVIDER = "Google Translation API";

    private final GoogleTranslationClient googleTranslationClient;
    private final CommonErrorLogRepository commonErrorLogRepository;

    // 텍스트를 targetLanguage로 번역. 외부 API 실패 시 CommonErrorLog 기록 후 EXTERNAL_SERVICE_ERROR
    public TranslationResponse translate(String content, LanguageCode targetLanguage) {
        try {
            String translated = googleTranslationClient.translate(content, targetLanguage);
            return new TranslationResponse(translated, targetLanguage);
        } catch (TranslationClientException e) {
            saveErrorLog(e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // 번역 실패 로그 저장 — REQUIRES_NEW로 외부 트랜잭션 롤백 시에도 로그 커밋 보장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrorLog(TranslationClientException e) {
        try {
            commonErrorLogRepository.save(CommonErrorLog.builder()
                    .domain(CommonErrorDomain.TRANSLATION)
                    .errorType(CommonErrorType.API_CALL_FAILURE)
                    .message(e.getMessage())
                    .detail(e.getCause() != null ? e.getCause().getMessage() : null)
                    .externalProvider(EXTERNAL_PROVIDER)
                    .build());
        } catch (Exception logEx) {
            log.error("CommonErrorLog 저장 실패. 번역 실패 로그가 누락됩니다.", logEx);
        }
    }
}
