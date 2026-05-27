package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 공개 상태 화이트리스트(ON_SALE·SOLD_OUT), category 필터(null=전체), cursor 기반 페이징.
     *  블랙리스트(HIDDEN 제외) 대신 화이트리스트 — 신규 status 추가 시 의도적 결정 강제. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status IN :visibleStatuses
            AND (:category IS NULL OR p.category = :category)
            AND (:cursorId IS NULL OR p.id < :cursorId)
            ORDER BY p.id DESC
            """)
    List<Product> findVisibleProducts(
            @Param("visibleStatuses") Set<ProductStatus> visibleStatuses,
            @Param("category") String category,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
