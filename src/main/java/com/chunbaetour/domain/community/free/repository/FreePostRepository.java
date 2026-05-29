package com.chunbaetour.domain.community.free.repository;

import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FreePost p WHERE p.id = :id")
    Optional<FreePost> findByIdForUpdate(@Param("id") Long id);

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
}
