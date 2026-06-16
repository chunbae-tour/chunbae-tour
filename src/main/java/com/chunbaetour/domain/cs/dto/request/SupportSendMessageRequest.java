package com.chunbaetour.domain.cs.dto.request;

import com.chunbaetour.domain.cs.entity.SupportMessageType;

/**
 * STOMP 메시지 전송 요청 DTO (KAN-310).
 *
 * <p>messageType이 null이면 TEXT로 취급(기존 클라이언트 호환). IMAGE/FILE은 {@code fileUrl}에
 * {@code POST /api/v1/support/rooms/{supportRoomId}/files}(또는 admin 경로) 응답으로 받은 객체 키를 그대로 담아 전송한다 —
 * 이 키는 SupportMessageService가 해당 상담방 소유 여부를 검증한다(IDOR 방지).
 *
 * <p>null·blank·길이(>1000) 검증은 SupportMessageService에서 명시적으로 수행 (STOMP @Payload는 Bean Validation 미적용)
 *
 * @param messageType TEXT(기본값)/IMAGE/FILE
 * @param content     TEXT 필수, IMAGE/FILE은 캡션으로 선택 사용
 * @param fileUrl     IMAGE/FILE 필수 — 업로드 API가 반환한 객체 키
 * @param fileName    IMAGE/FILE 필수 — 업로드 API가 반환한 원본 파일명
 * @param fileSize    IMAGE/FILE 필수 — 업로드 API가 반환한 파일 크기(bytes)
 */
public record SupportSendMessageRequest(
        SupportMessageType messageType,
        String content,
        String fileUrl,
        String fileName,
        Long fileSize
) {
}
