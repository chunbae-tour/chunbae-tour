package com.chunbaetour.domain.translation.service;

import com.chunbaetour.domain.common.entity.CommonErrorLog;
import com.chunbaetour.domain.common.repository.CommonErrorLogRepository;
import com.chunbaetour.domain.common.type.CommonErrorDomain;
import com.chunbaetour.domain.common.type.CommonErrorType;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 번역 실패 로그 저장 전용 Bean — REQUIRES_NEW로 외부 트랜잭션과 독립 커밋
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationErrorLogWriter {

    private static final String EXTERNAL_PROVIDER = "Google Translation API";

    private final CommonErrorLogRepository commonErrorLogRepository;

    // 번역 실패 로그 저장 — 저장 실패 시 log.error만 남기고 예외 전파하지 않음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(TranslationClientException e) {
        try {
            String message = (e.getMessage() == null || e.getMessage().isBlank())
                    ? "Translation API 호출 실패"
                    : e.getMessage();
            commonErrorLogRepository.save(CommonErrorLog.builder()
                    .domain(CommonErrorDomain.TRANSLATION)
                    .errorType(CommonErrorType.API_CALL_FAILURE)
                    .message(message)
                    .detail(e.getCause() != null ? e.getCause().getMessage() : e.getMessage())
                    .externalProvider(EXTERNAL_PROVIDER)
                    .build());
        } catch (Exception logEx) {
            log.error("CommonErrorLog 저장 실패. 번역 실패 로그가 누락됩니다.", logEx);
        }
    }
}
