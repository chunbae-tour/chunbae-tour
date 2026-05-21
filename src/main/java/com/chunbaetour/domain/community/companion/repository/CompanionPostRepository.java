package com.chunbaetour.domain.community.companion.repository;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

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
}
