package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "current_members", nullable = false)
    private int currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Version
    private Long version;

    @Builder
    private ChatRoom(Long postId, Long ownerId, String title, String description, int maxMembers) {
        if (postId == null || ownerId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (title.length() > 50) {
            throw new BusinessException(ErrorCode.CHAT_TITLE_TOO_LONG);
        }
        if (maxMembers < 2 || maxMembers > 50) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_CAPACITY);
        }
        this.postId = postId;
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.maxMembers = maxMembers;
        this.currentMembers = 1;
        this.status = ChatRoomStatus.OPEN;
    }

    public void close() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        this.status = ChatRoomStatus.CLOSED;
    }

    public void markFull() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        if (this.currentMembers < this.maxMembers) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.status = ChatRoomStatus.FULL;
    }

    public void markOpen() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        if (this.currentMembers >= this.maxMembers) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.status = ChatRoomStatus.OPEN;
    }

    public void incrementMembers() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        if (this.currentMembers >= this.maxMembers) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_FULL);
        }
        this.currentMembers++;
        if (this.currentMembers >= this.maxMembers) {
            this.status = ChatRoomStatus.FULL;
        }
    }

    public void decrementMembers() {
        if (this.currentMembers == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.currentMembers--;
        if (this.status == ChatRoomStatus.FULL) {
            this.status = ChatRoomStatus.OPEN;
        }
    }
}
