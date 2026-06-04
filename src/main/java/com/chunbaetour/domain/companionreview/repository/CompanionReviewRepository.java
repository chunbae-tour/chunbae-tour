package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionReviewRepository extends JpaRepository<CompanionReview, Long> {

    // 중복 리뷰 확인 — (reviewer_id, target_user_id, chat_room_id) unique 제약과 이중 방어
    boolean existsByReviewerIdAndTargetUserIdAndChatRoomId(Long reviewerId, Long targetUserId, Long chatRoomId);
}
