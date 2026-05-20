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
        WriterInfo writer = new WriterInfo(
                author.getId(),
                author.getNickname(),
                author.getProfileImageUrl(),
                null
        );
        return new FreePostGetListResponse(
                post.getId(),
                post.getTitle(),
                post.getImageUrls(),
                writer,
                post.getCreatedAt()
        );
    }
}
