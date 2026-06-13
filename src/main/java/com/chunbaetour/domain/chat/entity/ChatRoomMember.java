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

    // ACTIVE 멤버(방장·일반 참여자 모두) 여부 — 비참여·퇴장·강퇴는 false
    public boolean isActiveMember() {
        return ChatMemberState.activeStates().contains(this.memberState);
    }

    // 강퇴 이력 여부 — 재참여 신청 차단 판단에 사용
    public boolean isKicked() {
        return this.memberState == ChatMemberState.MEMBER_KICKED;
    }

    // OWNER_ACTIVE도 일반 멤버처럼 MEMBER_LEFT 전환 허용 (09_정책_결정_기록.md) —
    // CLOSED 방 방장의 자발적 퇴장, OPEN/FULL 방 단독 방장의 auto-close 양쪽에서 호출됨.
    // KICKED/LEFT 덮어쓰기 방지.
    public void leave() {
        if (this.memberState == ChatMemberState.MEMBER_KICKED
                || this.memberState == ChatMemberState.MEMBER_LEFT) {
            throw new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);
        }
        this.memberState = ChatMemberState.MEMBER_LEFT;
        this.leftAt = LocalDateTime.now();
    }

    // 방장 위임 — 위임받는 멤버: MEMBER_ACTIVE만 OWNER_ACTIVE로 전환 가능 (본인·비활성 멤버는 차단)
    public void promoteToOwner() {
        if (this.memberState != ChatMemberState.MEMBER_ACTIVE) {
            throw new BusinessException(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);
        }
        this.memberState = ChatMemberState.OWNER_ACTIVE;
    }

    // 방장 위임 — 위임하는 멤버: OWNER_ACTIVE를 MEMBER_ACTIVE로 전환
    // 호출 시점에 이미 ChatRoom.ownerId와 ChatRoomMember.memberState가 동기화돼있어야 하므로,
    // 이 분기는 그 둘이 불일치하는 서버측 데이터 정합성 버그 — 클라이언트 요청 오류(400)가 아닌 서버 오류(500)로 처리
    public void demoteFromOwner() {
        if (this.memberState != ChatMemberState.OWNER_ACTIVE) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        this.memberState = ChatMemberState.MEMBER_ACTIVE;
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
