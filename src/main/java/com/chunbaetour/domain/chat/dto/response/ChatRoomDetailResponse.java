package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.profileimage.ProfileImageDisplaySupport;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            String nickname,
            String profileImageUrl,
            ChatMemberState memberState,
            LocalDateTime joinedAt
    ) {
        // account null — 탈퇴 계정 fallback (ChatRoomMemberResponse.from()과 동일 패턴)
        public static MemberInfo from(ChatRoomMember member, Account account) {
            if (account == null) {
                return new MemberInfo(
                        member.getUserId(), "탈퇴한 사용자", null,
                        member.getMemberState(), member.getJoinedAt());
            }
            return new MemberInfo(
                    member.getUserId(),
                    account.getNickname(),
                    ProfileImageDisplaySupport.toDisplayUrl(account.getProfileImageUrl()),
                    member.getMemberState(),
                    member.getJoinedAt()
            );
        }
    }

    public static ChatRoomDetailResponse from(
            ChatRoom room, List<ChatRoomMember> activeMembers, Long userId, Map<Long, Account> accountMap) {
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
                activeMembers.stream()
                        .map(m -> MemberInfo.from(m, accountMap.get(m.getUserId())))
                        .toList()
        );
    }
}
