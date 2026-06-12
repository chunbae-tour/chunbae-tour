package com.chunbaetour.domain.community.free.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "free_posts", indexes = {
        // 목록 조회: WHERE status=? AND id<cursor ORDER BY id DESC
        @Index(name = "idx_free_status_id", columnList = "status, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreePost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 페이지 최대 크기 변경 시 함께 조정 필요
    @BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "free_post_images", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FreePostStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static FreePost create(Long authorId, String title, String content, List<String> imageUrls) {
        FreePost post = new FreePost();
        post.authorId = authorId;
        post.title = title;
        post.content = content;
        post.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
        post.status = FreePostStatus.ACTIVE;
        return post;
    }

    public void update(String title, String content, List<String> imageUrls) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (imageUrls != null) {
            this.imageUrls.clear();
            this.imageUrls.addAll(imageUrls);
        }
    }

    public void delete() {
        this.status = FreePostStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    /** 관리자 신고 처리 비공개 (status = HIDDEN). 사용자 삭제(DELETED)와 구분. */
    public void hide() {
        if (this.status == FreePostStatus.DELETED) {
            throw new IllegalStateException("삭제된 게시글은 숨김 처리할 수 없습니다. postId=" + this.id);
        }
        this.status = FreePostStatus.HIDDEN;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.authorId.equals(accountId);
    }

    public boolean isVisible() {
        return status != FreePostStatus.DELETED && status != FreePostStatus.HIDDEN;
    }
}
