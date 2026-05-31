package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.Faq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // CS-2 Admin: 카테고리별 전체 조회 (활성/비활성 포함)
    List<Faq> findByCategoryOrderByIdAsc(String category);

    // CS-3 User: 활성 FAQ만 카테고리별 조회
    List<Faq> findByCategoryAndIsActiveTrueOrderByIdAsc(String category);

    // CS-3 User: 전체 활성 FAQ 조회
    List<Faq> findByIsActiveTrueOrderByIdAsc();
}
