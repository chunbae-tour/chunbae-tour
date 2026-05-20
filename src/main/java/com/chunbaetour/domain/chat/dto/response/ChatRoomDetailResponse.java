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
        int currentMembers,
        int maxMembers,
        ChatRoomStatus status,
        List<MemberInfo> members
) {
    public record MemberInfo(
            Long userId,
            ChatMemberState memberState,
            LocalDateTime joinedAt
    ) {
        public static MemberInfo from(ChatRoomMember member) {
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
                room.getCurrentMembers(),
                room.getMaxMembers(),
                room.getStatus(),
                activeMembers.stream().map(MemberInfo::from).toList()
        );
    }
}
