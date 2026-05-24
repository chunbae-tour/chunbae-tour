package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import jakarta.persistence.LockModeType;
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

    // TODO [DB 마이그레이션]: payment_order_id 전체 unique 제약 제거 후 서비스 레이어 검사로 대체 중.
    //                        이상적인 해결책은 status = 'PENDING' 조건의 partial unique index.
    //                        MySQL 8.4는 partial index 미지원 → 별도 컬럼/함수 인덱스로 구현하거나 현행 유지.

    /** 환불 승인/거절 시 비관적 락 조회 (동시 처리 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdWithLock(@Param("id") Long id);

    /** 관리자 환불 목록 cursor 첫 페이지 (cursor 없을 때) */
    @Query("SELECT r FROM Refund r ORDER BY r.id DESC")
    List<Refund> findAllOrderByIdDesc(Pageable pageable);

    /** 관리자 환불 목록 cursor 다음 페이지 */
    @Query("SELECT r FROM Refund r WHERE r.id < :cursorId ORDER BY r.id DESC")
    List<Refund> findByIdLessThanOrderByIdDesc(@Param("cursorId") Long cursorId, Pageable pageable);

    /** 사용자 환불 목록 — 상태 필터 없음, 첫 페이지 */
    List<Refund> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    /** 사용자 환불 목록 — 상태 필터 없음, cursor 다음 페이지 */
    List<Refund> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursorId, Pageable pageable);

    /** 사용자 환불 목록 — 상태 필터, 첫 페이지 */
    List<Refund> findByUserIdAndStatusOrderByIdDesc(Long userId, RefundStatus status, Pageable pageable);

    /** 사용자 환불 목록 — 상태 필터, cursor 다음 페이지 */
    List<Refund> findByUserIdAndStatusAndIdLessThanOrderByIdDesc(Long userId, RefundStatus status, Long cursorId, Pageable pageable);
}
