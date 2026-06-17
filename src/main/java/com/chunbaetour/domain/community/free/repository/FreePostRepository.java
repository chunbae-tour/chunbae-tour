package com.chunbaetour.domain.community.free.repository;

import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    /**
     * 조회수 원자적 +1. 벌크 UPDATE라 엔티티 dirty checking을 거치지 않아
     * {@code @LastModifiedDate updatedAt}을 건드리지 않고(조회=수정 오염 방지),
     * 동시 조회 시 lost-update도 발생하지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE FreePost p SET p.viewCount = p.viewCount + 1 "
            + "WHERE p.id = :id AND p.status = com.chunbaetour.domain.community.free.entity.FreePostStatus.ACTIVE")
    void incrementViewCount(@Param("id") Long id);

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FreePost p WHERE p.id = :id")
    Optional<FreePost> findByIdForUpdate(@Param("id") Long id);

    /** 단건 상세 조회용 — imageUrls JOIN FETCH로 N+1 방지. */
    @Query("SELECT p FROM FreePost p LEFT JOIN FETCH p.imageUrls WHERE p.id = :id")
    Optional<FreePost> findByIdWithImages(@Param("id") Long id);

    @Query("""
            SELECT p FROM FreePost p
            WHERE p.status = :status
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<FreePost> findByCursor(
            @Param("status") FreePostStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FreePost p SET p.status = com.chunbaetour.domain.community.free.entity.FreePostStatus.HIDDEN, p.updatedAt = :now WHERE p.authorId = :authorId AND p.status = com.chunbaetour.domain.community.free.entity.FreePostStatus.ACTIVE")
    void hideAllActiveByAuthorId(@Param("authorId") Long authorId, @Param("now") LocalDateTime now);

    /**
     * 고아 판정용 — 객체 키가 어느 글에든 참조되는지(KAN-317 reconcile 스케줄러). 미참조면 고아 후보.
     * free_post_images(@ElementCollection)를 JOIN해 image_url(=iu)로 조회 → idx_free_post_images_image_url 사용.
     * (native EXISTS는 MySQL서 BIGINT를 반환해 boolean 매핑이 깨지므로 JPQL COUNT&gt;0으로 boolean을 직접 산출한다.)
     */
    @Query("SELECT COUNT(p) > 0 FROM FreePost p JOIN p.imageUrls iu WHERE iu = :key")
    boolean existsByImageKey(@Param("key") String key);
}
