package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** HIDDEN 제외, category 필터(null=전체), cursor 기반 페이징 */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status <> :hiddenStatus
            AND (:category IS NULL OR p.category = :category)
            AND (:cursorId IS NULL OR p.id < :cursorId)
            ORDER BY p.id DESC
            """)
    List<Product> findVisibleProducts(
            @Param("hiddenStatus") ProductStatus hiddenStatus,
            @Param("category") String category,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
