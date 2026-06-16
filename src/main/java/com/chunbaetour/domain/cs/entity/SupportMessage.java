package com.chunbaetour.domain.cs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "support_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "support_room_id", nullable = false)
    private Long supportRoomId;

    // 메시지 발신자 — USER 또는 ADMIN의 userId
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 10)
    private SupportSenderRole senderRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private SupportMessageType messageType;

    // TEXT 타입: 필수. IMAGE/FILE 타입: 선택(캡션)
    @Column(columnDefinition = "TEXT")
    private String content;

    // IMAGE/FILE 타입에만 사용 — TEXT 타입은 null
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    // IMAGE/FILE 타입에 사용 — 메시지 표시용 원본 파일명 (KAN-310)
    @Column(name = "file_name", length = 255)
    private String fileName;

    // IMAGE/FILE 타입에 사용 — 파일 크기(bytes) (KAN-310)
    @Column(name = "file_size")
    private Long fileSize;

    @CreatedDate
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Builder
    private SupportMessage(Long supportRoomId, Long senderId, SupportSenderRole senderRole,
                           SupportMessageType messageType, String content, String fileUrl,
                           String fileName, Long fileSize) {
        if (supportRoomId == null) throw new IllegalArgumentException("supportRoomId must not be null");
        if (senderId == null) throw new IllegalArgumentException("senderId must not be null");
        if (senderRole == null) throw new IllegalArgumentException("senderRole must not be null");
        if (messageType == null) throw new IllegalArgumentException("messageType must not be null");
        validatePayload(messageType, content, fileUrl, fileName, fileSize);
        this.supportRoomId = supportRoomId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.messageType = messageType;
        this.content = content;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    private static void validatePayload(SupportMessageType messageType, String content, String fileUrl,
                                          String fileName, Long fileSize) {
        if (messageType == SupportMessageType.TEXT) {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("TEXT 메시지는 content가 필요합니다.");
            }
            if (fileUrl != null) {
                throw new IllegalArgumentException("TEXT 메시지는 fileUrl을 가질 수 없습니다.");
            }
        }
        if ((messageType == SupportMessageType.IMAGE || messageType == SupportMessageType.FILE)
                && (fileUrl == null || fileUrl.isBlank())) {
            throw new IllegalArgumentException("IMAGE/FILE 메시지는 fileUrl이 필요합니다.");
        }
        if ((messageType == SupportMessageType.IMAGE || messageType == SupportMessageType.FILE)
                && (fileName == null || fileName.isBlank() || fileSize == null || fileSize <= 0)) {
            throw new IllegalArgumentException("IMAGE/FILE 메시지는 fileName/fileSize가 필요합니다.");
        }
    }
}
