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
    /**
     * @param imageUrls 저장된 객체 키를 presigned GET URL로 변환한 목록(PostImageService.presignAll). 비공개 버킷이라
     *                  엔티티의 키(post.getImageUrls())를 그대로 노출하지 않고 변환값을 담는다(KAN-317).
     */
    public static FreePostGetOneResponse of(
            FreePost post, Account author, long viewCount, long commentCount, List<String> imageUrls) {
        return new FreePostGetOneResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                imageUrls,
                WriterInfo.from(author),
                viewCount,
                commentCount,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
