package com.chunbaetour.domain.companion.repository;

import com.chunbaetour.domain.companion.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CompanionParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    // 동행 참여자 목록 — 시작/조회 응답 구성에 사용
    List<CompanionParticipant> findByCompanionId(Long companionId);

    // endParticipation 호출자가 해당 동행의 참여자인지 확인(CR_013)
    boolean existsByCompanionIdAndUserId(Long companionId, Long userId);

    // reviewer/target 동시 참여자 검증 — IN절 단일 쿼리로 참여 여부 확인(고도화 #25)
    long countByCompanionIdAndUserIdIn(Long companionId, List<Long> userIds);

    // 동행 취소 시 참여자 전체 하드 삭제 — 벌크 DELETE, 1차 캐시 stale 엔티티 방지 위해 clearAutomatically
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CompanionParticipant cp WHERE cp.companionId = :companionId")
    void deleteByCompanionId(@Param("companionId") Long companionId);

    // reviewer/target 둘 다 endParticipation을 마쳤는지 검증(CR_011, 고도화 #2)
    long countByCompanionIdAndUserIdInAndEndedAtIsNotNull(Long companionId, List<Long> userIds);

    // 참여자 본인 동행 종료 — endedAt이 null일 때만 세팅, 영향 행 수 반환 (0이면 이미 종료 처리됨, CR_015)
    // updatedAt 명시적 갱신 — JPQL 벌크 UPDATE는 JPA Auditing 우회 (audit L3)
    @Transactional
    @Modifying
    @Query("UPDATE CompanionParticipant cp SET cp.endedAt = CURRENT_TIMESTAMP, cp.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE cp.companionId = :companionId AND cp.userId = :userId AND cp.endedAt IS NULL")
    int endParticipationIfNotEnded(@Param("companionId") Long companionId, @Param("userId") Long userId);

    // 대상 유저들이 참여 중인 다른 ONGOING 동행의 여행 기간 — 기간 겹침 검증(CR_010)에 사용, excludeCompanionId는 본인 동행 제외용(없으면 null)
    @Query("""
            SELECT cp.userId AS userId, c.tripStartDate AS tripStartDate, c.tripEndDate AS tripEndDate
            FROM CompanionParticipant cp
            JOIN Companion c ON c.id = cp.companionId
            WHERE cp.userId IN :userIds
              AND c.status = com.chunbaetour.domain.companion.type.CompanionStatus.ONGOING
              AND (:excludeCompanionId IS NULL OR c.id <> :excludeCompanionId)
            """)
    List<CompanionTripPeriodProjection> findOngoingTripPeriodsByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("excludeCompanionId") Long excludeCompanionId);
}
