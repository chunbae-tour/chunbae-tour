package com.chunbaetour.domain.cs.dto.request;

// STOMP 메시지 전송 요청 DTO — TEXT 타입 전용 (IMAGE/FILE은 S3 연동 후 별도 티켓)
// null·blank·길이(>1000) 검증은 SupportMessageService에서 명시적으로 수행 (STOMP @Payload는 Bean Validation 미적용)
public record SupportSendMessageRequest(String content) {}
