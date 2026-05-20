package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.orderUid = :orderUid")
    Optional<PaymentOrder> findByOrderUidWithLock(@Param("orderUid") String orderUid);
}
