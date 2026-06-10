package com.chunbaetour.domain.like.repository;

import com.chunbaetour.domain.like.entity.UserLike;
import com.chunbaetour.domain.like.type.LikeTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, LikeTargetType targetType, Long targetId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserLike ul WHERE ul.user.id = :userId AND ul.targetType = :targetType AND ul.targetId = :targetId")
    int deleteByUserIdAndTargetTypeAndTargetId(@Param("userId") Long userId, @Param("targetType") LikeTargetType targetType, @Param("targetId") Long targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserLike ul WHERE ul.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Query("SELECT ul.targetId FROM UserLike ul WHERE ul.user.id = :userId AND ul.targetType = :targetType")
    Page<Long> findTargetIdsByUserIdAndTargetType(@Param("userId") Long userId, @Param("targetType") LikeTargetType targetType, Pageable pageable);
}
