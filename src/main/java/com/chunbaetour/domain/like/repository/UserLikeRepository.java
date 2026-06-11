package com.chunbaetour.domain.like.repository;

import com.chunbaetour.domain.like.dto.CategoryCount;
import com.chunbaetour.domain.like.entity.UserLike;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.type.PlaceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /**
     * 유저가 찜한 관광지(PLACE)들의 카테고리별 count를 내림차순으로 조회한다. (최대 3건)
     * <p>
     * Phase 9-2 검색 결과 개인화에 활용: 가장 많이 찜한 카테고리를 선호 카테고리로 판단하여
     * 검색 결과 상단에 해당 카테고리의 결과를 노출한다.
     * </p>
     * <p>
     * <b>[구현 노트]</b><br>
     * JPQL {@code JOIN ... ON}은 UserLike-Place 간 연관관계가 없어 Cross JOIN으로 실행될 위험이 있으므로
     * Native Query를 사용한다. LIMIT 3을 DB 레벨에서 처리하여 서비스 레이어에서 중복 slice를 제거한다.
     * </p>
     *
     * @param userId 유저 PK
     * @return 카테고리별 찜 count Projection 목록 (count 내림차순, 최대 3건)
     */
    @Query(value = "SELECT p.category AS category, COUNT(ul.id) AS count " +
                   "FROM user_likes ul " +
                   "JOIN places p ON ul.target_id = p.id " +
                   "WHERE ul.user_id = :userId AND ul.target_type = 'PLACE' " +
                   "GROUP BY p.category " +
                   "ORDER BY count DESC " +
                   "LIMIT 3",
           nativeQuery = true)
    List<CategoryCount> findLikedPlaceCategoryCountsByUserId(@Param("userId") Long userId);
}
