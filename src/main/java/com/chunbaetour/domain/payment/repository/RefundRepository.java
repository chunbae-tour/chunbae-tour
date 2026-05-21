package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 동일 주문에 대해 중복 환불 요청 방지 검사 */
    boolean existsByPaymentOrderIdAndStatus(Long paymentOrderId, RefundStatus status);

    /** 관리자 승인/거절 동시성 보호용 비관적 락 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdWithLock(@Param("id") Long id);

    /** 관리자 환불 목록 cursor 없을 때 (전체 최신순) */
    @Query("SELECT r FROM Refund r ORDER BY r.id DESC")
    List<Refund> findAllOrderByIdDesc(Pageable pageable);

    /** 관리자 환불 목록 cursor 있을 때 (keyset 페이징) */
    @Query("SELECT r FROM Refund r WHERE r.id < :cursorId ORDER BY r.id DESC")
    List<Refund> findByIdLessThanOrderByIdDesc(@Param("cursorId") Long cursorId, Pageable pageable);
}
