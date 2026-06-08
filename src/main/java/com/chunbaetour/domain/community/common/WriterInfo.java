package com.chunbaetour.domain.community.common;

import com.chunbaetour.domain.auth.Account;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WriterInfo(
        Long userId,
        String nickname,
        String profileImageUrl,
        Double companionScore
) {
    public static WriterInfo from(Account author) {
        if (author == null) return new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new WriterInfo(
                author.getId(),
                author.getNickname(),
                author.getProfileImageUrl(),
                (double) author.getCompanionScore()
        );
    }

    // 댓글 작성자용 — companionScore 미노출 (동행 게시글 전용 지표)
    public static WriterInfo fromComment(Account author) {
        if (author == null) return new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), null);
    }
}
