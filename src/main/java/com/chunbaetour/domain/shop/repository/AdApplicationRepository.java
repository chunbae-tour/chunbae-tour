package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdApplicationRepository extends JpaRepository<AdApplication, Long> {

    /**
     * PENDING 중복 체크용 — SELECT FOR UPDATE (current read).
     * REPEATABLE READ에서 일반 SELECT는 스냅샷을 읽으므로 동시 신청 시 중복을 놓칠 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdApplication a WHERE a.shopId = :shopId AND a.status = :status")
    List<AdApplication> findByShopIdAndStatusWithLock(
            @Param("shopId") Long shopId, @Param("status") AdApplicationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdApplication a WHERE a.id = :id")
    Optional<AdApplication> findByIdWithLock(@Param("id") Long id);

    /** 관리자 광고 신청 목록 — cursor keyset (id DESC), status=null이면 전체 조회 */
    @Query("""
            SELECT a FROM AdApplication a
            WHERE (:cursorId IS NULL OR a.id < :cursorId)
            AND (:status IS NULL OR a.status = :status)
            ORDER BY a.id DESC
            """)
    List<AdApplication> findAllWithCursor(@Param("cursorId") Long cursorId,
            @Param("status") AdApplicationStatus status, Pageable pageable);
}
