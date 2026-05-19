package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "translated_content", columnDefinition = "TEXT")
    private String translatedContent;

    @Column(name = "translate_lang", length = 10)
    private String translateLang;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    // senderId는 반드시 SecurityContext에서 추출 — WebSocket 클라이언트 전달값 사용 금지 (보안)
    // sentAt은 @CreatedDate 대신 직접 세팅 — Message는 AuditingEntityListener 미사용
    @Builder
    private Message(Long chatRoomId, Long senderId, MessageType messageType,
                    String content, String fileUrl, String fileName, Long fileSize) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.messageType = messageType;
        this.content = content;
        this.fileUrl = fileUrl;   // IMAGE/FILE 타입일 때만 값 존재
        this.fileName = fileName; // FILE 타입일 때만 값 존재
        this.fileSize = fileSize; // FILE 타입일 때만 값 존재
        this.sentAt = LocalDateTime.now();
    }
    // translatedContent, translateLang — 번역 요청 시 Translation 서비스가 별도로 채움 (생성자 외부)
}
