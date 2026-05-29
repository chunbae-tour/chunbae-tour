package com.chunbaetour.domain.common.type;

// common_error_logs 에러 유형
public enum CommonErrorType {
    API_CALL_FAILURE,       // 외부 API 호출 자체 실패
    RATE_LIMIT_EXCEEDED,    // 외부 API 요청 한도 초과
    TIMEOUT,                // 외부 API 응답 타임아웃
    INTERNAL                // 내부 처리 오류 (직렬화 실패 등)
}
