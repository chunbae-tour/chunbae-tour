package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.ShopNotice;
import java.time.LocalDateTime;

public record ShopNoticeResponse(
        Long noticeId,
        String title,
        String content,
        LocalDateTime createdAt
) {
    public static ShopNoticeResponse from(ShopNotice notice) {
        return new ShopNoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt()
        );
    }
}
