package com.chunbaetour.domain.merchant.repository;

import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication, Long> {

    /** 동일 유저의 중복 신청 방지 검사 (PENDING 또는 APPROVED 존재 여부) */
    boolean existsByUserIdAndStatusIn(Long userId, List<MerchantApplicationStatus> statuses);
}
