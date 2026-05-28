package com.chunbaetour.domain.translation.client;

// Google Translation API 호출 실패 — 서비스 레이어에서 BusinessException(EXTERNAL_SERVICE_ERROR)으로 변환
public class TranslationClientException extends RuntimeException {

    // 원인 예외(cause)와 함께 생성 — RestClientException 래핑 시 사용
    public TranslationClientException(String message, Throwable cause) {
        super(message, cause);
    }

    // 원인 예외 없이 생성 — 빈 응답 등 API 계약 위반 시 사용
    public TranslationClientException(String message) {
        super(message);
    }
}
