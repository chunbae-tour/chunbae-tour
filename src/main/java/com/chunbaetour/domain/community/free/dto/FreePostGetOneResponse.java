package com.chunbaetour.domain.community.free.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.free.entity.FreePost;
import java.time.LocalDateTime;
import java.util.List;

public record FreePostGetOneResponse(
        Long postId,
        String title,
        String content,
        List<String> imageUrls,
        WriterInfo writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FreePostGetOneResponse of(FreePost post, Account author) {
        WriterInfo writer = author != null
                ? new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), null)
                : new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new FreePostGetOneResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getImageUrls(),
                writer,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
