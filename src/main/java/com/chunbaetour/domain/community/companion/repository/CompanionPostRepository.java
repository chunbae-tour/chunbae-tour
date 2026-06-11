package com.chunbaetour.domain.community.companion.repository;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CompanionPost p WHERE p.id = :id")
    Optional<CompanionPost> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT p FROM CompanionPost p
            WHERE p.status = :status
              AND (:region IS NULL OR p.region = :region)
              AND (:meetingDate IS NULL OR p.meetingDate = :meetingDate)
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY p.id DESC
            """)
    List<CompanionPost> findByFilters(
            @Param("status") CompanionPostStatus status,
            @Param("region") String region,
            @Param("meetingDate") LocalDate meetingDate,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE CompanionPost p SET p.status = com.chunbaetour.domain.community.companion.entity.CompanionPostStatus.HIDDEN WHERE p.authorId = :authorId AND p.status = com.chunbaetour.domain.community.companion.entity.CompanionPostStatus.ACTIVE")
    void hideAllActiveByAuthorId(@Param("authorId") Long authorId);
}
