package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.Companion;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    // 채팅방당 동행 1번만 — 시작 전 중복 확인(CR_004) 및 진행 중 동행 조회에 사용
    Optional<Companion> findByChatRoomId(Long chatRoomId);

    // 동행 종료 시 비관적 락 — 동시 종료 요청 둘 다 ONGOING 읽고 end() 호출하는 경쟁 방어
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Companion c WHERE c.chatRoomId = :chatRoomId")
    Optional<Companion> findByChatRoomIdWithLock(@Param("chatRoomId") Long chatRoomId);

    // 매일 1회 배치job — tripEndDate < today인 ONGOING 동행을 일괄 ENDED 전환 (고도화 #2)
    // clearAutomatically — bulk update 후 1차 캐시에 남은 stale Companion 제거
    // updatedAt 명시적 갱신 — JPQL 벌크 UPDATE는 JPA Auditing 우회 (audit L3)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Companion c SET c.status = com.chunbaetour.domain.companionreview.type.CompanionStatus.ENDED, "
            + "c.endedAt = CURRENT_TIMESTAMP, c.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE c.status = com.chunbaetour.domain.companionreview.type.CompanionStatus.ONGOING AND c.tripEndDate < :today")
    int endExpiredCompanions(@Param("today") LocalDate today);
}
