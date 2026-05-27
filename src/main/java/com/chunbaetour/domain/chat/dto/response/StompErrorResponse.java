package com.chunbaetour.domain.chat.dto.response;

// STOMP @MessageExceptionHandler 에러 응답 — 클라이언트는 /user/queue/errors 구독으로 수신
public record StompErrorResponse(String errorCode, String message) {
}
