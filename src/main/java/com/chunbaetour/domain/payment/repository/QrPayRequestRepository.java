package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrPayRequestRepository extends JpaRepository<QrPayRequest, Long> {

    Optional<QrPayRequest> findByPayRequestId(String payRequestId);

    /** 동일 사용자·가게에 PENDING 요청이 이미 존재하는지 확인 — 중복 결제 요청 방지 */
    boolean existsByUserIdAndShopIdAndStatus(Long userId, Long shopId, QrPayStatus status);
}
