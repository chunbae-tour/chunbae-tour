package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.StoreOrder;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    /** 내 주문 내역 — cursor keyset 페이징 (id DESC) */
    @Query("""
            SELECT o FROM StoreOrder o
            WHERE o.userId = :userId
            AND (:cursorId IS NULL OR o.id < :cursorId)
            ORDER BY o.id DESC
            """)
    List<StoreOrder> findOrdersByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 특정 상품에 대한 사용자 누적 구매 수량 합산 — 1인 구매 한도 검증용 */
    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM StoreOrder o WHERE o.userId = :userId AND o.productId = :productId")
    int sumQuantityByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
