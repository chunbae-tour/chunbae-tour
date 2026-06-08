package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.Faq;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // CS-2 Admin: 커서 페이징 — 활성/비활성 포함, cursorId null 시 첫 페이지
    @Query("SELECT f FROM Faq f WHERE (:cursorId IS NULL OR f.id > :cursorId) ORDER BY f.id ASC")
    List<Faq> findWithCursor(@Param("cursorId") Long cursorId, Pageable pageable);

    // CS-3 User: 전체 활성 FAQ 커서 페이징
    @Query("SELECT f FROM Faq f WHERE f.isActive = true AND (:cursorId IS NULL OR f.id > :cursorId) ORDER BY f.id ASC")
    List<Faq> findByIsActiveTrueWithCursor(@Param("cursorId") Long cursorId, Pageable pageable);

    // CS-3 User: 카테고리별 활성 FAQ 커서 페이징
    @Query("SELECT f FROM Faq f WHERE f.isActive = true AND f.category = :category AND (:cursorId IS NULL OR f.id > :cursorId) ORDER BY f.id ASC")
    List<Faq> findByCategoryAndIsActiveTrueWithCursor(@Param("category") String category, @Param("cursorId") Long cursorId, Pageable pageable);
}
