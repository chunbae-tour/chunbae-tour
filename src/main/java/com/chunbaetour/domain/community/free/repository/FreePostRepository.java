package com.chunbaetour.domain.community.free.repository;

import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

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
