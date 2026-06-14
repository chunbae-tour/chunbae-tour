package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.type.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 구매 처리 시 재고 재검증용 — SELECT FOR UPDATE */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

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

    /** 관리자 목록 조회 — 전체 status 노출(HIDDEN 포함). status 필터(null=전체), category 필터(null=전체), cursor 페이징.
     *  공개 화이트리스트와 달리 status 제약 없음 — 숨김 상품 관리/복구를 위해 의도적으로 전체 노출. */
    @Query("""
            SELECT p FROM Product p
            WHERE (:status IS NULL OR p.status = :status)
            AND (:category IS NULL OR p.category = :category)
            AND (:cursorId IS NULL OR p.id < :cursorId)
            ORDER BY p.id DESC
            """)
    List<Product> findForAdmin(
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
