package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    /**
     * 1인 1리뷰 정책 체크: 해당 관광지에 사용자가 이미 ACTIVE 리뷰를 작성했는지 확인.
     * DB UNIQUE 제약(uk_place_review_active)과 비관적 락으로 이중 방어.
     */
    boolean existsByAuthorIdAndPlaceIdAndStatus(Long authorId, Long placeId, PlaceReviewStatus status);

    /**
     * 특정 관광지의 ACTIVE 리뷰 목록 페이징 조회 (최신순).
     * N+1 방지를 위해 author(Account) Fetch Join.
     * countQuery 분리: Fetch Join + Pageable 조합 시 HHH90003004 경고(in-memory pagination) 방지.
     */
    @Query(
        value = "SELECT r FROM PlaceReview r JOIN FETCH r.author WHERE r.place.id = :placeId AND r.status = 'ACTIVE'",
        countQuery = "SELECT COUNT(r) FROM PlaceReview r WHERE r.place.id = :placeId AND r.status = 'ACTIVE'"
    )
    Page<PlaceReview> findActiveByPlaceId(@Param("placeId") Long placeId, Pageable pageable);

    /**
     * 마이페이지 — 내가 작성한 ACTIVE 리뷰 목록 페이징 조회 (최신순).
     * N+1 방지를 위해 place Fetch Join.
     * countQuery 분리: Fetch Join + Pageable 조합 시 HHH90003004 경고(in-memory pagination) 방지.
     */
    @Query(
        value = """
            SELECT r
            FROM PlaceReview r
            JOIN FETCH r.place p
            WHERE r.author.id = :userId
              AND r.status = 'ACTIVE'
              AND p.status = 'ACTIVE'
            """,
        countQuery = """
            SELECT COUNT(r)
            FROM PlaceReview r
            WHERE r.author.id = :userId
              AND r.status = 'ACTIVE'
              AND r.place.status = 'ACTIVE'
            """
    )
    Page<PlaceReview> findActiveByAuthorId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 관광지 평점 재계산용: ACTIVE 리뷰의 평균 별점 합계와 개수를 한 번에 조회.
     */
    @Query("SELECT SUM(r.rating), COUNT(r) FROM PlaceReview r WHERE r.place.id = :placeId AND r.status = 'ACTIVE'")
    List<Object[]> sumRatingAndCount(@Param("placeId") Long placeId);

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PlaceReview r WHERE r.id = :id")
    Optional<PlaceReview> findByIdForUpdate(@Param("id") Long id);
}
