package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
}
