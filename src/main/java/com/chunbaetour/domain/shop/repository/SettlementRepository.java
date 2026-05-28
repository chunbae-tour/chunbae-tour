package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** PENDING 중복 체크용 — SELECT FOR UPDATE로 current read 보장 (REPEATABLE READ 스냅샷 우회) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Settlement s WHERE s.shopId = :shopId AND s.status = :status")
    List<Settlement> findByShopIdAndStatusWithLock(
            @Param("shopId") Long shopId, @Param("status") SettlementStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Settlement s WHERE s.id = :id")
    Optional<Settlement> findByIdWithLock(@Param("id") Long id);

    /** 상인 내 정산 내역 — cursor keyset (id DESC) */
    @Query("""
            SELECT s FROM Settlement s
            WHERE s.shopId = :shopId
            AND (:cursorId IS NULL OR s.id < :cursorId)
            ORDER BY s.id DESC
            """)
    List<Settlement> findByShopId(@Param("shopId") Long shopId,
            @Param("cursorId") Long cursorId, Pageable pageable);

    /** 관리자 정산 목록 — cursor keyset (id DESC) */
    @Query("""
            SELECT s FROM Settlement s
            WHERE (:cursorId IS NULL OR s.id < :cursorId)
            ORDER BY s.id DESC
            """)
    List<Settlement> findAllWithCursor(@Param("cursorId") Long cursorId, Pageable pageable);
}
