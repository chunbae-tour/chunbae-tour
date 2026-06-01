package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** PENDING 상태 인증 신청 수 — S10 대시보드 의존 (KAN-204에서 메서드 노출까지만). */
    long countByStatus(ShopCertificationStatus status);
}
