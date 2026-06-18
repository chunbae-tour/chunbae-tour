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
        long viewCount,
        long commentCount,
        LocalDateTime createdAt
) {
    /**
     * @param imageUrls 저장된 객체 키를 presigned GET URL로 변환한 목록(PostImageService.presignAll, KAN-317).
     */
    public static FreePostGetListResponse of(
            FreePost post, Account author, long commentCount, List<String> imageUrls) {
        return new FreePostGetListResponse(
                post.getId(),
                post.getTitle(),
                imageUrls,
                WriterInfo.from(author),
                post.getViewCount(),
                commentCount,
                post.getCreatedAt()
        );
    }
}
