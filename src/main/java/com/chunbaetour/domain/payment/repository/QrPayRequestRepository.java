package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
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

    /** 상인 가게 목록의 미만료 PENDING 결제 요청 조회 — 만료 시각 오름차순(먼저 만료되는 요청 우선 표시).
     *  expiredAt > now 조건으로 스케줄러 60초 지연 구간의 만료 건 제외 — 상인이 승인 시도 시
     *  confirmQrPayRequest 만료 가드에서 거부되는 UX 불일치 방지 */
    @Query("""
            SELECT q FROM QrPayRequest q
            WHERE q.shopId IN :shopIds AND q.status = :status AND q.expiredAt > :now
            ORDER BY q.expiredAt ASC, q.id ASC
            """)
    List<QrPayRequest> findPendingByShopIds(@Param("shopIds") List<Long> shopIds,
                                            @Param("status") QrPayStatus status,
                                            @Param("now") LocalDateTime now,
                                            Pageable pageable);

    /** 상인 홈 대시보드 — 오늘 완료된 QR 결제 합계 */
    @Query("""
            SELECT COALESCE(SUM(q.amount), 0)
            FROM QrPayRequest q
            WHERE q.shopId IN :shopIds
              AND q.status = :status
              AND q.completedAt >= :startAt
              AND q.completedAt < :endAt
            """)
    Long sumAmountByShopIdsAndStatusBetween(@Param("shopIds") List<Long> shopIds,
                                            @Param("status") QrPayStatus status,
                                            @Param("startAt") LocalDateTime startAt,
                                            @Param("endAt") LocalDateTime endAt);

    /**
     * 상인 홈 대시보드 — 기간 내 완료된 QR 결제 경량 목록(최신순).
     * 매출 합계·시간대별 분포·최근 결제 목록을 한 번의 조회로 함께 산출하기 위해 menu_items를 제외한 뷰로 반환한다.
     */
    @Query("""
            SELECT q.payRequestId AS payRequestId, q.shopId AS shopId,
                   q.amount AS amount, q.completedAt AS completedAt
            FROM QrPayRequest q
            WHERE q.shopId IN :shopIds
              AND q.status = :status
              AND q.completedAt >= :startAt
              AND q.completedAt < :endAt
            ORDER BY q.completedAt DESC, q.id DESC
            """)
    List<CompletedQrPayView> findCompletedByShopsBetween(@Param("shopIds") List<Long> shopIds,
                                                         @Param("status") QrPayStatus status,
                                                         @Param("startAt") LocalDateTime startAt,
                                                         @Param("endAt") LocalDateTime endAt);

    /**
     * 상인 홈 대시보드 — 기간 내 접수돼 미완료(거절+만료)로 끝난 QR 결제 건수 (KAN-283).
     * 거절/만료는 completedAt이 null이라 완료 시각으로 집계할 수 없다.
     * 만료는 스케줄러 bulk UPDATE로 전이돼 updatedAt(@LastModifiedDate)이 갱신되지 않으므로,
     * 거절·만료를 일관되게 집계하려고 항상 set되는 createdAt(접수 시각) 기준으로 센다.
     * QR 만료는 접수 +5분이라 createdAt과 전이 시각이 사실상 같다.
     */
    @Query("""
            SELECT COUNT(q)
            FROM QrPayRequest q
            WHERE q.shopId IN :shopIds
              AND q.status IN :statuses
              AND q.createdAt >= :startAt
              AND q.createdAt < :endAt
            """)
    long countByShopsAndStatusesBetween(@Param("shopIds") List<Long> shopIds,
                                        @Param("statuses") Collection<QrPayStatus> statuses,
                                        @Param("startAt") LocalDateTime startAt,
                                        @Param("endAt") LocalDateTime endAt);
}
