package com.chunbaetour.domain.chat.dto.request;

// WebSocket STOMP 메시지 전송 요청 DTO — TEXT 타입 전용 (IMAGE/FILE은 S-11c 파일 업로드 후 연동)
public record ChatSendMessageRequest(String content) {
}
