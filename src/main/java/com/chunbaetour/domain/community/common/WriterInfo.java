package com.chunbaetour.domain.community.common;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.profileimage.ProfileImageDisplaySupport;
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
                // 저장값(우리 키→presigned / 외부 URL→passthrough) 표시 변환 (KAN-320)
                ProfileImageDisplaySupport.toDisplayUrl(author.getProfileImageUrl()),
                (double) author.getCompanionScore()
        );
    }

    // 댓글 작성자용 — companionScore 미노출 (동행 게시글 전용 지표)
    public static WriterInfo fromComment(Account author) {
        if (author == null) return new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new WriterInfo(author.getId(), author.getNickname(),
                ProfileImageDisplaySupport.toDisplayUrl(author.getProfileImageUrl()), null);
    }
}
