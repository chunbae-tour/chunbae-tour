package com.chunbaetour.domain.merchant.repository;

import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication, Long> {

    /** 동일 유저의 중복 신청 방지 검사 (PENDING 또는 APPROVED 존재 여부) */
    boolean existsByUserIdAndStatusIn(Long userId, List<MerchantApplicationStatus> statuses);

    /** 동일 사업자번호가 이미 PENDING 또는 APPROVED 상태인지 검사 (cross-user 중복 방지) */
    boolean existsByBusinessNumberAndStatusIn(String businessNumber, List<MerchantApplicationStatus> statuses);

    // TODO [STORY-09]: 관리자 신청 목록 조회 — PENDING 상태 목록, 커서 기반 페이지네이션
    //   예시: List<MerchantApplication> findByStatusOrderByIdDesc(MerchantApplicationStatus status, Pageable pageable)
    //         List<MerchantApplication> findByIdLessThanAndStatusOrderByIdDesc(Long cursorId, MerchantApplicationStatus status, Pageable pageable)

    // TODO [STORY-09]: 승인 시 비관적 락 조회 — 동시 승인/거절 race condition 방지
    //   예시: @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<MerchantApplication> findByIdWithLock(Long id)
}
