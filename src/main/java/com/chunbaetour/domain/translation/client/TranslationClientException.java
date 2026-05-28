package com.chunbaetour.domain.translation.client;

// Google Translation API 호출 실패 — 서비스 레이어에서 BusinessException(EXTERNAL_SERVICE_ERROR)으로 변환
public class TranslationClientException extends RuntimeException {

    public TranslationClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public TranslationClientException(String message) {
        super(message);
    }
}
