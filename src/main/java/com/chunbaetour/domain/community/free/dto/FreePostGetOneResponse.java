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
        long viewCount,
        long commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FreePostGetOneResponse of(FreePost post, Account author, long viewCount, long commentCount) {
        return new FreePostGetOneResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getImageUrls(),
                WriterInfo.from(author),
                viewCount,
                commentCount,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
