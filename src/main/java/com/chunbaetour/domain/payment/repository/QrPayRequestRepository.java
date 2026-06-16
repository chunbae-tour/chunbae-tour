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
     * 상인 홈 대시보드 — 기간 내 미완료(거절+만료)로 끝난 QR 결제 건수 (KAN-283).
     *
     * <p>집계 윈도우 컬럼으로 createdAt이 아니라 <b>expiredAt</b>을 쓴다. createdAt은 JPA auditing
     * (@CreatedDate, JVM 기본 존)으로 기록돼 운영(-Duser.timezone=Asia/Seoul)에선 KST wall-clock인 반면,
     * 호출 측이 넘기는 경계(startAt/endAt)와 completedAt/expiredAt은 Clock(systemUTC) 기준 UTC wall-clock이다.
     * createdAt을 UTC 경계와 비교하면 9시간 밀려 오후 3시(KST) 이후 거절·만료가 당일 카운터에서 누락된다.
     * expiredAt은 생성 시 UTC clock으로 (접수 +5분)이 항상 set(NOT NULL)되고 REJECTED/EXPIRED 모두 보존되므로
     * 존이 일관된다. createdAt 기준 대비 윈도우가 5분 시프트되나 일 단위 카운터에는 무해하다.
     */
    @Query("""
            SELECT COUNT(q)
            FROM QrPayRequest q
            WHERE q.shopId IN :shopIds
              AND q.status IN :statuses
              AND q.expiredAt >= :startAt
              AND q.expiredAt < :endAt
            """)
    long countByShopsAndStatusesBetween(@Param("shopIds") List<Long> shopIds,
                                        @Param("statuses") Collection<QrPayStatus> statuses,
                                        @Param("startAt") LocalDateTime startAt,
                                        @Param("endAt") LocalDateTime endAt);
}
