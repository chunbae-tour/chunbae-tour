package com.chunbaetour.domain.community.comment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.community.common.PostType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 10)
    private PostType postType;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommentStatus status;

    // soft delete 시각 기록 — status=DELETED로 숨기되 행은 보존 (대댓글 parent 참조 무결성 유지)
    // LocalDateTime.now()는 서버 타임존에 의존 — 테스트 시각 제어 필요 시 Clock 주입 고려
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Comment create(Long postId, PostType postType, Long authorId, String content) {
        Comment comment = new Comment();
        comment.postId = postId;
        comment.postType = postType;
        comment.authorId = authorId;
        comment.content = content;
        comment.status = CommentStatus.ACTIVE;
        return comment;
    }

    public void delete() {
        this.status = CommentStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long accountId) {
        return this.authorId.equals(accountId);
    }
}
