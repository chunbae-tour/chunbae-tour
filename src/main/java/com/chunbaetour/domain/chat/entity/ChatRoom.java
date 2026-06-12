package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "chat_rooms",
    uniqueConstraints = @UniqueConstraint(name = "uk_chat_rooms_post_id", columnNames = "post_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    // CLOSED 방의 ownerId는 별도로 갱신되지 않음 — 방장이 leaveRoom()으로 퇴장(OWNER_ACTIVE → MEMBER_LEFT)해도
    // "이력상 마지막 방장"을 가리키는 값으로 그대로 유지된다 (09_정책_결정_기록.md)
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

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoomMember> members = new ArrayList<>();

    public static ChatRoom createWithOwner(Long postId, Long ownerId, String title, String description, int maxMembers) {
        ChatRoom chatRoom = ChatRoom.builder()
                .postId(postId)
                .ownerId(ownerId)
                .title(title)
                .description(description)
                .maxMembers(maxMembers)
                .build();
        chatRoom.members.add(ChatRoomMember.ofOwner(chatRoom, ownerId));
        return chatRoom;
    }

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

    // 방장 여부 확인 — NPE 방지를 위해 ownerId 기준으로 equals 호출
    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    // 신청 가능 상태인지 검증 — CLOSED/FULL 방은 참여 신청 자체를 차단
    public void validateJoinable() {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        if (this.status == ChatRoomStatus.FULL) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_FULL);
        }
    }

    // 방장 위임 — ownerId 교체. 멤버 상태(OWNER_ACTIVE/MEMBER_ACTIVE) 교체는 서비스에서 ChatRoomMember 양쪽에 별도 적용
    public void transferOwner(Long newOwnerId) {
        if (this.ownerId.equals(newOwnerId)) {
            throw new BusinessException(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);
        }
        this.ownerId = newOwnerId;
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
