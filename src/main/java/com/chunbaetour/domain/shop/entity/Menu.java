package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.shop.dto.request.MenuUpdateRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "menus")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable = true;

    // QR 결제 내역에서 menuId 참조 보존 — hard delete 대신 soft delete 사용
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Menu(Long shopId, String name, String description, Long price, String imageUrl) {
        this.shopId = shopId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.isAvailable = true;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * null 필드는 기존 값 유지 (부분 수정 지원).
     * name: 앞뒤 공백 자동 trim.
     * description: null = 수정 안 함, "" = 소개글 삭제 (선택 필드, 빈 값 허용).
     * imageUrl: null = 수정 안 함, "" = 이미지 삭제 (null로 저장).
     */
    public void update(MenuUpdateRequest request) {
        if (request.name() != null) this.name = request.name().trim();
        if (request.description() != null) this.description = request.description();
        if (request.price() != null) this.price = request.price();
        if (request.imageUrl() != null) this.imageUrl = request.imageUrl().isBlank() ? null : request.imageUrl();
        if (request.isAvailable() != null) this.isAvailable = request.isAvailable();
    }
}
