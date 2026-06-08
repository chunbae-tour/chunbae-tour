package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    // 동행 참여자 목록 — 시작/조회 응답 구성에 사용
    List<CompanionParticipant> findByCompanionId(Long companionId);

    // 참여자 여부 확인 — CompanionReview 작성 권한 검증(고도화 #25)에 사용
    boolean existsByCompanionIdAndUserId(Long companionId, Long userId);
}
