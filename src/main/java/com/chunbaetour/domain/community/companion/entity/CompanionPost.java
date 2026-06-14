package com.chunbaetour.domain.community.companion.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companion_posts")
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
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
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
        // placeId·placeName은 쌍으로만 수정 가능 — 한쪽만 보내면 장소 정보 불일치
        if ((placeId == null) != (placeName == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (maxMembers != null && maxMembers < this.currentMembers) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (placeId != null) this.placeId = placeId;
        if (placeName != null) this.placeName = placeName;
        if (region != null) this.region = region;
        if (meetingDate != null) this.meetingDate = meetingDate;
        if (maxMembers != null) {
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
