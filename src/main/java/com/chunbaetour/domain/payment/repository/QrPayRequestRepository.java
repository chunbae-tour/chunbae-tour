package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrPayRequestRepository extends JpaRepository<QrPayRequest, Long> {

    Optional<QrPayRequest> findByPayRequestId(String payRequestId);

    /** 동일 사용자·가게에 PENDING 요청이 이미 존재하는지 확인 — 중복 결제 요청 방지 */
    boolean existsByUserIdAndShopIdAndStatus(Long userId, Long shopId, QrPayStatus status);

    /**
     * 만료 시각이 지난 PENDING 요청을 EXPIRED로 일괄 전환 — STORY-15 스케줄러에서 호출.
     * pendingKey null 처리로 unique 제약 해제 → 이후 동일 사용자·가게 재결제 가능.
     */
    @Modifying
    @Query("UPDATE QrPayRequest q SET q.status = :expiredStatus, q.pendingKey = null WHERE q.status = :pendingStatus AND q.expiredAt < :now")
    int bulkExpireOverdue(@Param("pendingStatus") QrPayStatus pendingStatus,
                          @Param("expiredStatus") QrPayStatus expiredStatus,
                          @Param("now") LocalDateTime now);
}
