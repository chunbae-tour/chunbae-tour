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
        // 요청자 본인의 상태 — 클라이언트가 방장 여부 및 UI 권한 분기에 사용
        ChatMemberState myMemberState,
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

    public static ChatRoomDetailResponse from(ChatRoom room, List<ChatRoomMember> activeMembers, Long userId) {
        // 요청자의 memberState — isMember 검증 이후 호출되므로 반드시 존재
        ChatMemberState myMemberState = activeMembers.stream()
                .filter(m -> m.getUserId().equals(userId))
                .map(ChatRoomMember::getMemberState)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("member not found after isMember check"));

        return new ChatRoomDetailResponse(
                room.getId(),
                room.getPostId(),
                room.getTitle(),
                room.getDescription(),
                room.getOwnerId(),
                room.getMaxMembers(),
                room.getStatus(),
                myMemberState,
                activeMembers.stream().map(MemberInfo::from).toList()
        );
    }
}
