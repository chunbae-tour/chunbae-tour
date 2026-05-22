package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    @Query("SELECT p FROM PaymentOrder p WHERE p.orderUid = :orderUid")
    Optional<PaymentOrder> findByOrderUid(@Param("orderUid") String orderUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.orderUid = :orderUid")
    Optional<PaymentOrder> findByOrderUidWithLock(@Param("orderUid") String orderUid);

    /** 환불 승인 시 PaymentOrder 상태 변경용 비관적 락 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.id = :id")
    Optional<PaymentOrder> findByIdWithLock(@Param("id") Long id);
}
