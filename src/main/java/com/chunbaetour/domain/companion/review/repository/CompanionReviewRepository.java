package com.chunbaetour.domain.companion.review.repository;

import com.chunbaetour.domain.companion.review.entity.CompanionReview;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionReviewRepository extends JpaRepository<CompanionReview, Long> {

    // 중복 리뷰 확인 — (reviewer_id, target_user_id, chat_room_id) unique 제약과 이중 방어
    boolean existsByReviewerIdAndTargetUserIdAndChatRoomId(Long reviewerId, Long targetUserId, Long chatRoomId);

    // scoreDistribution — score별 리뷰 건수 집계 (CR-3)
    @Query("SELECT r.score as score, COUNT(r) as count FROM CompanionReview r WHERE r.targetUserId = :targetUserId GROUP BY r.score")
    List<ScoreCountProjection> countByScoreForTargetUser(@Param("targetUserId") Long targetUserId);

    // 동행 리뷰 목록 cursor 페이징 — id DESC(최신순), cursorId null 시 첫 페이지(상한 없음)
    @Query("SELECT r FROM CompanionReview r WHERE r.targetUserId = :targetUserId AND (:cursorId IS NULL OR r.id < :cursorId) ORDER BY r.id DESC")
    List<CompanionReview> findByTargetUserIdWithCursor(
            @Param("targetUserId") Long targetUserId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
