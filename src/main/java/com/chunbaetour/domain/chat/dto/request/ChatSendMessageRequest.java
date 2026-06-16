package com.chunbaetour.domain.chat.dto.request;

import com.chunbaetour.domain.chat.type.MessageType;

/**
 * WebSocket STOMP 메시지 전송 요청 DTO (KAN-309).
 *
 * <p>messageType이 null이면 TEXT로 취급(기존 클라이언트 호환). IMAGE/FILE은 {@code fileUrl}에
 * {@code POST /api/v1/chat/rooms/{roomId}/files} 응답으로 받은 객체 키를 그대로 담아 전송한다 — 이 키는
 * ChatMessageService가 해당 채팅방 소유 여부를 검증한다(IDOR 방지).
 *
 * @param messageType TEXT(기본값)/IMAGE/FILE — SYSTEM은 클라이언트가 지정할 수 없다.
 * @param content     TEXT 필수, IMAGE/FILE은 캡션으로 선택 사용
 * @param fileUrl     IMAGE/FILE 필수 — 업로드 API가 반환한 객체 키
 * @param fileName    FILE 필수 — 업로드 API가 반환한 원본 파일명
 * @param fileSize    FILE 필수 — 업로드 API가 반환한 파일 크기(bytes)
 */
public record ChatSendMessageRequest(
        MessageType messageType,
        String content,
        String fileUrl,
        String fileName,
        Long fileSize
) {
}
