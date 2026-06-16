package com.chunbaetour.domain.cs.dto.response;

/**
 * 상담 채팅 파일/이미지 업로드 응답 (KAN-310).
 *
 * @param fileUrl     업로드된 파일의 <b>S3 객체 키</b>(예: {@code support-rooms/10/uuid.jpg}).
 *                     버킷이 완전 비공개라 public URL이 아닌 키를 반환한다 — 클라이언트는 이 값을 그대로
 *                     {@code SupportSendMessageRequest.fileUrl}에 담아 STOMP로 전송한다. 메시지 조회/브로드캐스트
 *                     응답에서는 서버가 이 키를 presigned GET URL로 변환해 내려준다(만료 있는 URL은 저장하지 않음).
 * @param fileName    원본 파일명 (메시지 표시용)
 * @param fileSize    파일 크기 (bytes)
 * @param contentType 검증된 content-type
 */
public record SupportFileUploadResponse(String fileUrl, String fileName, Long fileSize, String contentType) {
}
