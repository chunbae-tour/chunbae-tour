package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가게 공지 엔티티 (KAN-213).
 * 상인이 자신의 가게에 등록하는 공지사항. 삭제는 hard delete.
 */
@Entity
@Table(name = "shop_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private ShopNotice(Long shopId, String title, String content) {
        this.shopId = shopId;
        this.title = title;
        this.content = content;
    }

    /** 공지 등록 팩토리. */
    public static ShopNotice create(Long shopId, String title, String content) {
        return ShopNotice.builder()
                .shopId(shopId)
                .title(title)
                .content(content)
                .build();
    }
}
