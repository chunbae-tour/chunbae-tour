package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 동일 주문에 대해 중복 환불 요청 방지 검사 */
    boolean existsByPaymentOrderIdAndStatus(Long paymentOrderId, RefundStatus status);

    // TODO [DB 마이그레이션]: payment_order_id 전체 unique 제약 제거 후 서비스 레이어 검사로 대체 중.
    //                        이상적인 해결책은 status = 'PENDING' 조건의 partial unique index.
    //                        MySQL 8.4는 partial index 미지원 → 별도 컬럼/함수 인덱스로 구현하거나 현행 유지.
}
