package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 동일 주문에 대해 중복 환불 요청 방지 검사 */
    boolean existsByPaymentOrderIdAndStatus(Long paymentOrderId, RefundStatus status);
}
