package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 만료 시각이 지난 PENDING 요청을 EXPIRED로 일괄 전환 — STORY-15 스케줄러에서 호출.
     * pendingKey null 처리로 unique 제약 해제 → 이후 동일 사용자·가게 재결제 가능.
     */
    @Modifying
    @Query("UPDATE QrPayRequest q SET q.status = :expiredStatus, q.pendingKey = null WHERE q.status = :pendingStatus AND q.expiredAt <= :now")
    int bulkExpireOverdue(@Param("pendingStatus") QrPayStatus pendingStatus,
                          @Param("expiredStatus") QrPayStatus expiredStatus,
                          @Param("now") LocalDateTime now);

    /** 상인 홈 대시보드 — 오늘 완료된 QR 결제 합계 */
    @Query("""
            SELECT COALESCE(SUM(q.amount), 0)
            FROM QrPayRequest q
            WHERE q.shopId IN :shopIds
              AND q.status = :status
              AND q.updatedAt >= :startAt
              AND q.updatedAt < :endAt
            """)
    Long sumAmountByShopIdsAndStatusBetween(@Param("shopIds") List<Long> shopIds,
                                            @Param("status") QrPayStatus status,
                                            @Param("startAt") LocalDateTime startAt,
                                            @Param("endAt") LocalDateTime endAt);

    /** 상인 홈 대시보드 — 최근 완료 QR 결제 최대 10건 */
    List<QrPayRequest> findTop10ByShopIdInAndStatusOrderByUpdatedAtDescIdDesc(
            List<Long> shopIds, QrPayStatus status);
}
