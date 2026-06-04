package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionReviewRepository extends JpaRepository<CompanionReview, Long> {

    // 중복 리뷰 확인 — (reviewer_id, target_user_id, chat_room_id) unique 제약과 이중 방어
    boolean existsByReviewerIdAndTargetUserIdAndChatRoomId(Long reviewerId, Long targetUserId, Long chatRoomId);

    // scoreDistribution — score별 리뷰 건수 집계 (CR-3)
    @Query("SELECT r.score, COUNT(r) FROM CompanionReview r WHERE r.targetUserId = :targetUserId GROUP BY r.score")
    List<Object[]> countByScoreForTargetUser(@Param("targetUserId") Long targetUserId);
}
