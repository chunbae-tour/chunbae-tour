package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    // 동행 참여자 목록 — 시작/조회 응답 구성에 사용
    List<CompanionParticipant> findByCompanionId(Long companionId);

    // reviewer/target 동시 참여자 검증 — IN절 단일 쿼리로 참여 여부 확인(고도화 #25)
    long countByCompanionIdAndUserIdIn(Long companionId, List<Long> userIds);
}
