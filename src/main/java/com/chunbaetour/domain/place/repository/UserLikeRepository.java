package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.UserLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    /**
     * 찜 취소 단일 쿼리.
     * SELECT(exists) → DELETE 이중 조회 없이 삭제된 row 수를 반환해
     * 0이면 LIKE_NOT_FOUND로 처리한다.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserLike ul WHERE ul.user.id = :userId AND ul.place.id = :placeId")
    int deleteByUserIdAndPlaceId(@Param("userId") Long userId, @Param("placeId") Long placeId);

    /** 마이페이지 연동용 (3-3): 사용자가 찜한 장소 목록 페이징 조회 */
    Page<UserLike> findByUserId(Long userId, Pageable pageable);

    /** 
     * 마이페이지 연동용 (3-3): 사용자가 찜한 '활성화된' 관광지 목록 페이징 조회 
     * N+1 문제를 방지하기 위해 @EntityGraph로 Place를 Fetch Join 처리합니다.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "place")
    Page<UserLike> findByUserIdAndPlace_Status(Long userId, com.chunbaetour.domain.place.type.PlaceStatus status, Pageable pageable);
}
