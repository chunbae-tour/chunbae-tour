package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomDetailResponse(
        Long chatRoomId,
        Long postId,
        String title,
        String description,
        Long ownerId,
        int maxMembers,
        ChatRoomStatus status,
        // 현재 인원은 members.size()로 클라이언트가 계산 — 엔티티 카운트 필드와 이중 관리 방지
        List<MemberInfo> members
) {
    public record MemberInfo(
            Long userId,
            ChatMemberState memberState,
            LocalDateTime joinedAt
    ) {
        public static MemberInfo from(ChatRoomMember member) {
            // joinedAt은 BaseEntity.createdAt 재사용 — 현재 정책상 KICKED 재참여 불가(새 row 생성)이므로
            // createdAt = 마지막 참여 시점으로 의미가 맞음. 재참여 허용 정책으로 변경 시 별도 컬럼 필요
            return new MemberInfo(
                    member.getUserId(),
                    member.getMemberState(),
                    member.getCreatedAt()
            );
        }
    }

    public static ChatRoomDetailResponse from(ChatRoom room, List<ChatRoomMember> activeMembers) {
        return new ChatRoomDetailResponse(
                room.getId(),
                room.getPostId(),
                room.getTitle(),
                room.getDescription(),
                room.getOwnerId(),
                room.getMaxMembers(),
                room.getStatus(),
                activeMembers.stream().map(MemberInfo::from).toList()
        );
    }
}
