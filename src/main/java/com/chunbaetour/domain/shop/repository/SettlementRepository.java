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

    /**
     * PENDING 중복 체크용 — SELECT FOR UPDATE (current read).
     * MySQL REPEATABLE READ에서 일반 SELECT는 트랜잭션 시작 시점 스냅샷을 읽으므로,
     * 동시 요청이 커밋한 PENDING 행을 놓칠 수 있다. FOR UPDATE는 최신 커밋 데이터를 읽어 이를 방지.
     * 정상 운영 시 가게당 PENDING 건수는 0~1건이므로 락 범위는 사실상 단건 수준.
     */
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

    /** 관리자 정산 목록 — cursor keyset (id DESC), status=null이면 전체 조회 */
    @Query("""
            SELECT s FROM Settlement s
            WHERE (:cursorId IS NULL OR s.id < :cursorId)
            AND (:status IS NULL OR s.status = :status)
            ORDER BY s.id DESC
            """)
    List<Settlement> findAllWithCursor(@Param("cursorId") Long cursorId,
            @Param("status") SettlementStatus status, Pageable pageable);
}
