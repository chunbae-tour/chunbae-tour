package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrPayRequestRepository extends JpaRepository<QrPayRequest, Long> {

    Optional<QrPayRequest> findByPayRequestId(String payRequestId);

    /** 락 획득 후 상태·만료 재검증용 — 경합 상황에서 stale read 방지 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QrPayRequest q WHERE q.payRequestId = :payRequestId")
    Optional<QrPayRequest> findByPayRequestIdWithLock(@Param("payRequestId") String payRequestId);

    /** 동일 사용자·가게에 PENDING 요청이 이미 존재하는지 확인 — 중복 결제 요청 방지 */
    boolean existsByUserIdAndShopIdAndStatus(Long userId, Long shopId, QrPayStatus status);

    /** 만료되지 않은 PENDING 요청 존재 여부 — expiredAt > now 조건으로 만료 건 제외 (스케줄러 지연 대응) */
    boolean existsByUserIdAndShopIdAndStatusAndExpiredAtAfter(
            Long userId, Long shopId, QrPayStatus status, LocalDateTime now);
}
