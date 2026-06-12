package com.chunbaetour.domain.community.companion.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companion_posts", indexes = {
        // 기본 목록 (필터 없음): WHERE status=? AND id<cursor ORDER BY id DESC
        @Index(name = "idx_companion_status_id", columnList = "status, id"),
        // region 필터: WHERE status=? AND region=? ... ORDER BY id DESC
        @Index(name = "idx_companion_status_region_id", columnList = "status, region, id"),
        // meetingDate 필터: WHERE status=? AND meeting_date=? ... ORDER BY id DESC
        @Index(name = "idx_companion_status_meeting_date_id", columnList = "status, meeting_date, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(length = 50)
    private String region;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "current_members", nullable = false)
    private int currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CompanionPostStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static CompanionPost create(
            Long authorId, String title, String content,
            Long placeId, String placeName, String region,
            LocalDate meetingDate, int maxMembers) {
        if (maxMembers < 2) {
            throw new IllegalArgumentException("maxMembers must be at least 2");
        }
        CompanionPost post = new CompanionPost();
        post.authorId = authorId;
        post.title = title;
        post.content = content;
        post.placeId = placeId;
        post.placeName = placeName;
        post.region = region;
        post.meetingDate = meetingDate;
        post.maxMembers = maxMembers;
        post.currentMembers = 1;
        post.status = CompanionPostStatus.ACTIVE;
        return post;
    }

    public void update(String title, String content, Long placeId, String placeName,
                       String region, LocalDate meetingDate, Integer maxMembers) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (placeId != null) this.placeId = placeId;
        if (placeName != null) this.placeName = placeName;
        if (region != null) this.region = region;
        if (meetingDate != null) this.meetingDate = meetingDate;
        if (maxMembers != null) {
            if (maxMembers < this.currentMembers) {
                throw new IllegalArgumentException("maxMembers must be >= currentMembers");
            }
            this.maxMembers = maxMembers;
        }
    }

    public void delete() {
        this.status = CompanionPostStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    /** 관리자 신고 처리 비공개 (status = HIDDEN). 사용자 삭제(DELETED)와 구분. */
    public void hide() {
        if (this.status == CompanionPostStatus.DELETED) {
            throw new IllegalStateException("삭제된 게시글은 숨김 처리할 수 없습니다. postId=" + this.id);
        }
        this.status = CompanionPostStatus.HIDDEN;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.authorId.equals(accountId);
    }

    public boolean isVisible() {
        return status != CompanionPostStatus.DELETED && status != CompanionPostStatus.HIDDEN;
    }
}
