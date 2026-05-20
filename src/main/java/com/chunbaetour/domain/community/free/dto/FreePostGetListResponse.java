package com.chunbaetour.domain.community.free.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.free.entity.FreePost;
import java.time.LocalDateTime;
import java.util.List;

public record FreePostGetListResponse(
        Long postId,
        String title,
        List<String> imageUrls,
        WriterInfo writer,
        LocalDateTime createdAt
) {
    public static FreePostGetListResponse of(FreePost post, Account author) {
        WriterInfo writer = author != null
                ? new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), null)
                : new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new FreePostGetListResponse(
                post.getId(),
                post.getTitle(),
                post.getImageUrls(),
                writer,
                post.getCreatedAt()
        );
    }
}
