package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상인 인증 신청 저장소 (KAN-204, Admin Epic KAN-177 S05).
 */
public interface ShopCertificationRepository extends JpaRepository<ShopCertification, Long> {

    /**
     * 운영자 인증 신청 목록 — status 필터 + cursor 페이징 (id 내림차순 keyset).
     *
     * <p>{@code status}가 null이면 전체. {@code cursorId}보다 작은 id만 조회해 다음 페이지를 sentinel(size+1)
     * 방식으로 판단(서비스 책임). {@code AccountRepository.searchForAdmin}의 cursor 페이징 패턴 재사용 — id DESC +
     * {@code (:cursorId IS NULL OR id < :cursorId)}.
     */
    @Query("SELECT c FROM ShopCertification c WHERE "
            + "(:status IS NULL OR c.status = :status) "
            + "AND (:cursorId IS NULL OR c.id < :cursorId) "
            + "ORDER BY c.id DESC")
    List<ShopCertification> searchForAdmin(@Param("status") ShopCertificationStatus status,
                                           @Param("cursorId") Long cursorId,
                                           Pageable pageable);

    /**
     * 상태 전이용 단건 조회 — PESSIMISTIC_WRITE 락 (KAN-204 S05 리뷰: 동시성).
     *
     * <p>두 운영자가 같은 신청을 동시에 approve/reject/cancel 하면 둘 다 PENDING/APPROVED를 읽고 통과해
     * status·is_certified가 모순될 수 있다. 행 락으로 직렬화 — 한 쪽은 대기 후 INVALID_STATUS 가드에 걸린다.
     * 단순 조회(list/detail GET)는 락 없는 {@code findById} 사용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ShopCertification c WHERE c.id = :id")
    Optional<ShopCertification> findByIdForUpdate(@Param("id") Long id);

    /** PENDING 상태 인증 신청 수 — S10 대시보드 의존 (KAN-204에서 메서드 노출까지만). */
    long countByStatus(ShopCertificationStatus status);
}
