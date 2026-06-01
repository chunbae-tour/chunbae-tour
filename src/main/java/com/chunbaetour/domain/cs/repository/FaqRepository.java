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

    // CS-3 User: 활성 FAQ만 카테고리별 조회
    List<Faq> findByCategoryAndIsActiveTrueOrderByIdAsc(String category);

    // CS-3 User: 전체 활성 FAQ 조회
    List<Faq> findByIsActiveTrueOrderByIdAsc();
}
