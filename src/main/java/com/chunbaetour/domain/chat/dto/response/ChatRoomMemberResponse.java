package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import java.time.LocalDateTime;

public record ChatRoomMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        float companionScore,
        ChatMemberState memberState,
        LocalDateTime joinedAt
) {
    public static ChatRoomMemberResponse from(ChatRoomMember member, Account account) {
        return new ChatRoomMemberResponse(
                member.getUserId(),
                account.getNickname(),
                account.getProfileImageUrl(),
                account.getCompanionScore(),
                member.getMemberState(),
                member.getCreatedAt()
        );
    }
}
