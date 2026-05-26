package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import java.time.LocalDateTime;

// 멤버 전체 프로필 응답 — Account 일괄 조회 비용을 감수하는 전용 endpoint용.
// 방 단건 조회(ChatRoomDetailResponse.MemberInfo)는 userId/memberState만 포함하는 경량 형태 사용.
// joinedAt — 재참여(reactivate) 시 갱신되는 마지막 참여 시점.
public record ChatRoomMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        float companionScore,
        ChatMemberState memberState,
        LocalDateTime joinedAt
) {
    // account null — 탈퇴 계정 fallback (Community WriterInfo.from() 동일 패턴)
    public static ChatRoomMemberResponse from(ChatRoomMember member, Account account) {
        if (account == null) {
            return new ChatRoomMemberResponse(
                    member.getUserId(), "탈퇴한 사용자", null, 0f,
                    member.getMemberState(), member.getJoinedAt());
        }
        return new ChatRoomMemberResponse(
                member.getUserId(),
                account.getNickname(),
                account.getProfileImageUrl(),
                account.getCompanionScore(),
                member.getMemberState(),
                member.getJoinedAt()
        );
    }
}
