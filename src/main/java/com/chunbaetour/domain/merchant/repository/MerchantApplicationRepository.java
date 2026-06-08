package com.chunbaetour.domain.merchant.repository;

import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication, Long> {

    /** 상태별 신청 수 — S06 대시보드 PENDING 카운트 호출용 (Spring Data 파생 쿼리). */
    long countByStatus(MerchantApplicationStatus status);

    /** 동일 유저의 중복 신청 방지 검사 (PENDING 또는 APPROVED 존재 여부) */
    boolean existsByUserIdAndStatusIn(Long userId, List<MerchantApplicationStatus> statuses);

    /** 동일 사업자번호가 이미 PENDING 또는 APPROVED 상태인지 검사 (cross-user 중복 방지) */
    boolean existsByBusinessNumberAndStatusIn(String businessNumber, List<MerchantApplicationStatus> statuses);

    /** 첫 페이지: PENDING 목록, id 내림차순 */
    List<MerchantApplication> findByStatusOrderByIdDesc(MerchantApplicationStatus status, Pageable pageable);

    /** 다음 페이지: cursorId 미만의 PENDING 목록, id 내림차순 */
    List<MerchantApplication> findByStatusAndIdLessThanOrderByIdDesc(
            MerchantApplicationStatus status, Long cursorId, Pageable pageable);

    /** 동시 승인/거절 race condition 방지용 비관적 락 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MerchantApplication m WHERE m.id = :id")
    Optional<MerchantApplication> findByIdWithLock(@Param("id") Long id);
}
