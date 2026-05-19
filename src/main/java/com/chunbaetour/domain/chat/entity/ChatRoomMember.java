package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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
@Table(name = "chat_room_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_state", nullable = false, length = 20)
    private ChatMemberState memberState;

    @CreatedDate
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // memberState는 호출부에서 OWNER_ACTIVE / MEMBER_ACTIVE 명시적으로 지정
    @Builder
    private ChatRoomMember(Long chatRoomId, Long userId, ChatMemberState memberState) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.memberState = memberState;
    }

    // 자발적 퇴장 — leftAt 기록, 재참여 가능.
    // KICKED 상태에서 leave()로 덮어쓰면 "강퇴 재참여 불가" 규칙이 깨지므로 차단.
    public void leave() {
        if (this.memberState == ChatMemberState.MEMBER_KICKED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberState = ChatMemberState.MEMBER_LEFT;
        this.leftAt = LocalDateTime.now();
    }

    // 개설자 강퇴 — leftAt 기록, 재참여 영구 차단 (CHAT_010).
    // 이미 퇴장/강퇴된 멤버는 활동 상태가 아니므로 강퇴 대상 아님.
    public void kick() {
        if (this.memberState == ChatMemberState.MEMBER_LEFT
                || this.memberState == ChatMemberState.MEMBER_KICKED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberState = ChatMemberState.MEMBER_KICKED;
        this.leftAt = LocalDateTime.now();
    }
}
