package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "chat_room_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_state", nullable = false, length = 20)
    private ChatMemberState memberState;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // memberState는 호출부에서 OWNER_ACTIVE / MEMBER_ACTIVE 명시적으로 지정
    @Builder
    private ChatRoomMember(ChatRoom chatRoom, Long userId, ChatMemberState memberState) {
        if (chatRoom == null || userId == null || memberState == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.chatRoom = chatRoom;
        this.userId = userId;
        this.memberState = memberState;
        this.joinedAt = LocalDateTime.now();
    }

    public static ChatRoomMember ofOwner(ChatRoom chatRoom, Long userId) {
        return ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .userId(userId)
                .memberState(ChatMemberState.OWNER_ACTIVE)
                .build();
    }

    public static ChatRoomMember ofMember(ChatRoom chatRoom, Long userId) {
        return ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .userId(userId)
                .memberState(ChatMemberState.MEMBER_ACTIVE)
                .build();
    }

    public boolean isOwner() {
        return this.memberState == ChatMemberState.OWNER_ACTIVE;
    }

    // OWNER는 leave() 불가 — close()로만 방 종료 가능. KICKED/LEFT 덮어쓰기 방지.
    public void leave() {
        if (this.memberState == ChatMemberState.OWNER_ACTIVE) {
            throw new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_LEAVE);
        }
        if (this.memberState == ChatMemberState.MEMBER_KICKED
                || this.memberState == ChatMemberState.MEMBER_LEFT) {
            throw new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);
        }
        this.memberState = ChatMemberState.MEMBER_LEFT;
        this.leftAt = LocalDateTime.now();
    }

    // MEMBER_LEFT 상태 유저 재참여 수락 시 호출 — 새 레코드 INSERT 대신 기존 레코드 업데이트
    public void reactivate() {
        if (this.memberState != ChatMemberState.MEMBER_LEFT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberState = ChatMemberState.MEMBER_ACTIVE;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
    }

    // OWNER는 강퇴 불가 — close()로만 방 종료 가능
    // 이미 퇴장/강퇴된 멤버는 강퇴 대상 아님
    public void kick() {
        if (this.memberState == ChatMemberState.OWNER_ACTIVE) {
            throw new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_BE_KICKED);
        }
        if (this.memberState == ChatMemberState.MEMBER_LEFT
                || this.memberState == ChatMemberState.MEMBER_KICKED) {
            throw new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);
        }
        this.memberState = ChatMemberState.MEMBER_KICKED;
        this.leftAt = LocalDateTime.now();
    }
}
