package com.chunbaetour.domain.payment.repository;

import com.chunbaetour.domain.payment.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    @Query("SELECT p FROM PaymentOrder p WHERE p.orderUid = :orderUid")
    Optional<PaymentOrder> findByOrderUid(@Param("orderUid") String orderUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.orderUid = :orderUid")
    Optional<PaymentOrder> findByOrderUidWithLock(@Param("orderUid") String orderUid);

    /**
     * 멱등성 키로 주문 조회 — 상태 무관 (PENDING·COMPLETED·FAILED·CANCELLED·REFUNDED 모두 포함).
     *
     * <p>PENDING이면 재시도 재활용, 그 외 상태면 동일 키 재사용 → PAY_007.
     * DB UNIQUE 제약은 영구적이므로 Redis TTL(24h) 만료 후 동일 키 재사용 시 500이 아닌 명시적 오류 반환.
     */
    @Query("SELECT p FROM PaymentOrder p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<PaymentOrder> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 사용자 결제(충전) 내역 cursor 페이징 조회 — id DESC, cursorId IS NULL이면 첫 페이지 */
    @Query("""
            SELECT p FROM PaymentOrder p
            WHERE p.userId = :userId
            AND (:cursorId IS NULL OR p.id < :cursorId)
            ORDER BY p.id DESC
            """)
    List<PaymentOrder> findByUserIdWithCursor(@Param("userId") Long userId,
                                              @Param("cursorId") Long cursorId,
                                              Pageable pageable);

    /** 환불 승인 시 PaymentOrder 상태 변경용 비관적 락 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.id = :id")
    Optional<PaymentOrder> findByIdWithLock(@Param("id") Long id);

    /**
     * PENDING 또는 FAILED 상태인 경우에만 COMPLETED + pgTransactionId 세팅하는 DB-level 조건부 UPDATE.
     *
     * <p>Issue #114 release blocker 해소용. 기존 2-phase 조회(findByOrderUid → 락 조회) 패턴은
     * Phase 1의 cached PENDING entity가 Phase 2 락 획득 후에도 stale 상태로 반환되어 동시 webhook
     * 2건이 모두 COMPLETED 전환 분기를 통과 → walletService.charge 중복 실행.
     *
     * <p>본 메서드는 DB CAS(Compare-And-Swap)로 멱등성을 보장한다.
     * <ul>
     *   <li>동시 2건 호출 시: InnoDB row lock으로 직렬화 → 한쪽만 1 반환, 다른 쪽은 0 반환</li>
     *   <li>COMPLETED / REFUNDED / CANCELLED: 0 반환 (조용히 skip — 종착/취소 상태 보호)</li>
     *   <li>PENDING / FAILED → COMPLETED 허용 — handleFail 선점으로 FAILED여도 PG PAID 시 결제 유실 방지</li>
     * </ul>
     *
     * <p>WHERE 절을 명시적 화이트리스트(IN PENDING/FAILED)로 작성한 이유:
     * 기존 {@code status <> COMPLETED} 조건은 REFUNDED/CANCELLED까지 허용해 환불 완료된 주문을
     * 재완료시키는 회귀 위험. PR #198 코드 리뷰 (CodeRabbit + lim-haeun) 지적 반영.
     *
     * <p>{@code clearAutomatically=true}: UPDATE 실행 후 영속성 컨텍스트 자동 clear → caller가
     * 후속으로 같은 entity를 조회할 때 stale 캐시가 아닌 DB 최신 row 반환을 보장.
     *
     * @return 영향받은 row 수 (1 = 본 호출이 COMPLETED 전환 성공, 0 = 전환 가능 상태 아님 또는 row 없음)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentOrder p "
            + "SET p.status = com.chunbaetour.domain.payment.type.PaymentOrderStatus.COMPLETED, "
            + "    p.pgTransactionId = :pgTransactionId "
            + "WHERE p.orderUid = :orderUid "
            + "  AND p.status IN ("
            + "    com.chunbaetour.domain.payment.type.PaymentOrderStatus.PENDING, "
            + "    com.chunbaetour.domain.payment.type.PaymentOrderStatus.FAILED)")
    int completeIfNotCompleted(@Param("orderUid") String orderUid,
                                @Param("pgTransactionId") String pgTransactionId);

    /**
     * PENDING 상태인 경우에만 FAILED로 전환하는 DB-level 조건부 UPDATE.
     *
     * <p>이미 COMPLETED/FAILED/REFUNDED 상태이면 0 반환 — 멱등 처리.
     * 동시 webhook 2건 호출 시 한쪽만 1 반환 → 멱등성 키 해제 중복 방지에 활용.
     *
     * @return 영향받은 row 수 (1 = 본 호출이 FAILED 전환 성공, 0 = 이미 PENDING 아님)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentOrder p "
            + "SET p.status = com.chunbaetour.domain.payment.type.PaymentOrderStatus.FAILED "
            + "WHERE p.orderUid = :orderUid "
            + "  AND p.status = com.chunbaetour.domain.payment.type.PaymentOrderStatus.PENDING")
    int failIfPending(@Param("orderUid") String orderUid);

    /**
     * PENDING 상태인 경우에만 CANCELLED로 전환하는 DB-level 조건부 UPDATE (KAN-252, 사용자 직접 취소).
     *
     * <p>웹훅(COMPLETED/FAILED 전환)·자동 만료와의 경합 방어용. 사용자 취소 요청과 웹훅이 동시에 와도
     * InnoDB row lock으로 직렬화 → 한쪽만 1 반환. 이미 COMPLETED/FAILED/CANCELLED/REFUNDED 등 PENDING이
     * 아니면 0 반환 → 상태 전이 거절(PAYMENT_ORDER_NOT_CANCELLABLE).
     *
     * @return 영향받은 row 수 (1 = 본 호출이 CANCELLED 전환 성공, 0 = PENDING 아님 또는 row 없음)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentOrder p "
            + "SET p.status = com.chunbaetour.domain.payment.type.PaymentOrderStatus.CANCELLED "
            + "WHERE p.orderUid = :orderUid "
            + "  AND p.status = com.chunbaetour.domain.payment.type.PaymentOrderStatus.PENDING")
    int cancelIfPending(@Param("orderUid") String orderUid);
}
