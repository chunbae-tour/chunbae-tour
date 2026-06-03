package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 동일 주문에 대해 중복 환불 요청 방지 검사 */
    boolean existsByPaymentOrderIdAndStatus(Long paymentOrderId, RefundStatus status);

    Optional<Refund> findFirstByPaymentOrderIdOrderByIdDesc(Long paymentOrderId);

    // TODO [DB 마이그레이션]: payment_order_id 전체 unique 제약 제거 후 서비스 레이어 검사로 대체 중.
    //                        이상적인 해결책은 status = 'PENDING' 조건의 partial unique index.
    //                        MySQL 8.4는 partial index 미지원 → 별도 컬럼/함수 인덱스로 구현하거나 현행 유지.

    /** 환불 승인/거절 시 비관적 락 조회 (동시 처리 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdWithLock(@Param("id") Long id);

    /** 관리자 환불 목록 — cursorId null이면 전체 첫 페이지, 값 있으면 해당 id 이전 페이지 */
    @Query("SELECT r FROM Refund r WHERE (:cursorId IS NULL OR r.id < :cursorId) ORDER BY r.id DESC")
    List<Refund> findWithCursor(@Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 스케줄러 재시도 대상 조회 — FAILED이면서 next_retry_at이 도래하고 최대 횟수 미만인 건만.
     * idx_refunds_retry (status, next_retry_at) 인덱스 사용.
     */
    @Query("SELECT r FROM Refund r WHERE r.status = :status " +
           "AND r.retryCount < :maxRetryCount " +
           "AND r.nextRetryAt IS NOT NULL " +
           "AND r.nextRetryAt <= :now " +
           "ORDER BY r.nextRetryAt ASC")
    List<Refund> findRetryableRefunds(
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("status") RefundStatus status,
            Pageable pageable);

    /** 스케줄러 최초 처리 대상 — PENDING 상태 환불, 생성 순으로 처리 */
    @Query("SELECT r FROM Refund r WHERE r.status = :status ORDER BY r.createdAt ASC")
    List<Refund> findByStatusOrderByCreatedAt(
            @Param("status") RefundStatus status,
            Pageable pageable);

    /** 사용자 환불 목록 — status/cursor 모두 선택적 (null이면 조건 미적용) */
    @Query("SELECT r FROM Refund r WHERE r.userId = :userId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:cursorId IS NULL OR r.id < :cursorId) " +
           "ORDER BY r.id DESC")
    List<Refund> findByUserIdWithFilter(
            @Param("userId") Long userId,
            @Param("status") RefundStatus status,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
