package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);
}
